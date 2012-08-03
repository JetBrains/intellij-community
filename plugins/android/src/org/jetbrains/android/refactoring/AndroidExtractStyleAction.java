package org.jetbrains.android.refactoring;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.SdkConstants;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.dom.resources.StyleItem;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidExtractStyleAction extends BaseRefactoringAction {
  @NonNls public static final String ACTION_ID = "AndroidExtractStyleAction";

  private static String[] NON_EXTRACTABLE_ATTRIBUTES =
    new String[]{AndroidDomUtil.ATTR_ID, AndroidDomUtil.ATTR_TEXT, AndroidDomUtil.ATTR_HINT, AndroidDomUtil.ATTR_SRC,
      AndroidDomUtil.ATTR_ON_CLICK};

  private final MyTestConfig myTestConfig;

  public AndroidExtractStyleAction() {
    myTestConfig = null;
  }

  @TestOnly
  public AndroidExtractStyleAction(@Nullable MyTestConfig testConfig) {
    myTestConfig = testConfig;
  }

  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(PsiElement element, Editor editor, PsiFile file, DataContext context) {
    if (element == null ||
        AndroidFacet.getInstance(element) == null ||
        PsiTreeUtil.getParentOfType(element, XmlText.class) != null) {
      return false;
    }
    final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    return tag != null && isEnabled(tag);
  }

  @Override
  protected boolean isEnabledOnElements(PsiElement[] elements) {
    if (elements.length != 1) {
      return false;
    }
    final PsiElement element = elements[0];
    return element instanceof XmlTag &&
           AndroidFacet.getInstance(element) != null &&
           isEnabled((XmlTag)element);
  }

  public static boolean isEnabled(@NotNull XmlTag tag) {
    return getLayoutViewElement(tag) != null &&
           getExtractableAttributes(tag).size() > 0;
  }

  @Nullable
  private static LayoutViewElement getLayoutViewElement(@NotNull XmlTag tag) {
    final DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
    return domElement instanceof LayoutViewElement
           ? (LayoutViewElement)domElement
           : null;
  }

  @Override
  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    final XmlTag componentTag = getComponentTag(dataContext);
    return new MyHandler(myTestConfig, componentTag);
  }

  @Override
  public void update(AnActionEvent e) {
    final DataContext context = e.getDataContext();

    final DataContext patchedContext = new DataContext() {
      @Override
      public Object getData(@NonNls String dataId) {
        final Object data = context.getData(dataId);
        if (data != null) {
          return data;
        }
        if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
          return getComponentTag(context);
        }
        return null;
      }
    };
    super.update(new AnActionEvent(e.getInputEvent(), patchedContext, e.getPlace(), e.getPresentation(),
                                   e.getActionManager(), e.getModifiers()));
  }

  @Nullable
  private static XmlTag getComponentTag(DataContext dataContext) {
    if (dataContext == null) {
      return null;
    }

    for (AndroidRefactoringContextProvider provider : AndroidRefactoringContextProvider.EP_NAME.getExtensions()) {
      final XmlTag componentTag = provider.getComponentTag(dataContext);

      if (componentTag != null) {
        return componentTag;
      }
    }
    return null;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return language == XMLLanguage.INSTANCE;
  }

  @Override
  protected boolean isAvailableForFile(PsiFile file) {
    return file instanceof XmlFile &&
           AndroidFacet.getInstance(file) != null &&
           DomManager.getDomManager(file.getProject()).getDomFileDescription((XmlFile)file)
             instanceof LayoutDomFileDescription;
  }

  private static void doExtractStyle(@NotNull XmlTag viewTag, @Nullable MyTestConfig testConfig) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(viewTag);
    assert module != null;
    doExtractStyle(module, viewTag, true, testConfig);
  }

  @Nullable
  public static String doExtractStyle(@NotNull Module module,
                                      @NotNull final XmlTag viewTag,
                                      final boolean addStyleAttributeToTag,
                                      @Nullable MyTestConfig testConfig) {
    final PsiFile file = viewTag.getContainingFile();
    if (file == null) {
      return null;
    }
    final String dialogTitle = AndroidBundle.message("android.extract.style.title");
    final String fileName = AndroidResourceUtil.getDefaultResourceFileName(ResourceType.STYLE);
    assert fileName != null;
    final List<String> dirNames = Arrays.asList(ResourceFolderType.VALUES.getName());
    final List<XmlAttribute> extractableAttributes = getExtractableAttributes(viewTag);
    final Project project = module.getProject();

    if (extractableAttributes.size() == 0) {
      AndroidUtils.reportError(project, "The tag doesn't contain any attributes that can be extracted", dialogTitle);
      return null;
    }

    final LayoutViewElement viewElement = getLayoutViewElement(viewTag);
    assert viewElement != null;
    final ResourceValue parentStyleVlaue = viewElement.getStyle().getValue();
    final String parentStyle;
    boolean supportImplicitParent = false;

    if (parentStyleVlaue != null) {
      parentStyle = parentStyleVlaue.getResourceName();
      if (!ResourceType.STYLE.getName().equals(parentStyleVlaue.getResourceType()) || parentStyle == null || parentStyle.length() == 0) {
        AndroidUtils.reportError(project, "Invalid parent style reference " + parentStyleVlaue.toString(), dialogTitle);
        return null;
      }
      supportImplicitParent = parentStyleVlaue.getPackage() == null;
    }
    else {
      parentStyle = null;
    }

    final String styleName;
    final List<XmlAttribute> styledAttributes;
    final Module chosenModule;

    if (testConfig == null) {
      final ExtractStyleDialog dialog =
        new ExtractStyleDialog(module, fileName, supportImplicitParent ? parentStyle : null, dirNames, extractableAttributes);
      dialog.setTitle(dialogTitle);
      dialog.show();

      if (!dialog.isOK()) {
        return null;
      }
      chosenModule = dialog.getChosenModule();
      assert chosenModule != null;

      styledAttributes = dialog.getStyledAttributes();
      styleName = dialog.getStyleName();
    }
    else {
      testConfig.validate(extractableAttributes);

      chosenModule = testConfig.getModule();
      styleName = testConfig.getStyleName();
      final Set<String> attrsToExtract = new HashSet<String>(Arrays.asList(testConfig.getAttributesToExtract()));
      styledAttributes = new ArrayList<XmlAttribute>();

      for (XmlAttribute attribute : extractableAttributes) {
        if (attrsToExtract.contains(attribute.getName())) {
          styledAttributes.add(attribute);
        }
      }
    }
    final boolean[] success = {false};
    final boolean finalSupportImplicitParent = supportImplicitParent;

    new WriteCommandAction(project, "Extract Android Style '" + styleName + "'", file) {
      @Override
      protected void run(final Result result) throws Throwable {
        final List<XmlAttribute> attributesToDelete = new ArrayList<XmlAttribute>();

        if (!AndroidResourceUtil
          .createValueResource(chosenModule, styleName, ResourceType.STYLE, fileName, dirNames, new Processor<ResourceElement>() {
            @Override
            public boolean process(ResourceElement element) {
              assert element instanceof Style;
              final Style style = (Style)element;

              for (XmlAttribute attribute : styledAttributes) {
                if (SdkConstants.NS_RESOURCES.equals(attribute.getNamespace())) {
                  final StyleItem item = style.addItem();
                  item.getName().setStringValue("android:" + attribute.getLocalName());
                  item.setStringValue(attribute.getValue());
                  attributesToDelete.add(attribute);
                }
              }

              if (parentStyleVlaue != null && (!finalSupportImplicitParent || !styleName.startsWith(parentStyle + "."))) {
                final String aPackage = parentStyleVlaue.getPackage();
                 style.getParentStyle().setStringValue((aPackage != null ? aPackage + ":" : "") + parentStyle);
              }
              return true;
            }
          })) {
          return;
        }

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            for (XmlAttribute attribute : attributesToDelete) {
              attribute.delete();
            }
            if (addStyleAttributeToTag) {
              final LayoutViewElement viewElement = getLayoutViewElement(viewTag);
              assert viewElement != null;
              viewElement.getStyle().setStringValue("@style/" + styleName);
            }
          }
        });
        success[0] = true;
      }

      @Override
      protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
        return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
      }
    }.execute();

    return success[0] ? styleName : null;
  }

  @NotNull
  static List<XmlAttribute> getExtractableAttributes(@NotNull XmlTag viewTag) {
    final List<XmlAttribute> extractableAttributes = new ArrayList<XmlAttribute>();

    for (XmlAttribute attribute : viewTag.getAttributes()) {
      if (canBeExtracted(attribute)) {
        extractableAttributes.add(attribute);
      }
    }
    return extractableAttributes;
  }

  private static boolean canBeExtracted(@NotNull XmlAttribute attribute) {
    if (!(SdkConstants.NS_RESOURCES.equals(attribute.getNamespace()))) {
      return false;
    }
    final String name = attribute.getLocalName();
    if (ArrayUtil.find(NON_EXTRACTABLE_ATTRIBUTES, name) >= 0) {
      return false;
    }
    if (name.startsWith(AndroidDomUtil.ATTR_STYLE)) {
      return false;
    }
    return true;
  }

  private static class MyHandler implements RefactoringActionHandler {
    private final MyTestConfig myTestConfig;
    private final XmlTag myTag;

    private MyHandler(@Nullable MyTestConfig testConfig, @Nullable XmlTag tag) {
      myTestConfig = testConfig;
      myTag = tag;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
      if (myTag != null) {
        doExtractStyle(myTag, myTestConfig);
        return;
      }
      final PsiElement element = getElementAtCaret(editor, file);
      if (element == null) {
        return;
      }
      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      if (tag == null) {
        return;
      }
      doExtractStyle(tag, myTestConfig);
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
      if (myTag != null) {
        doExtractStyle(myTag, myTestConfig);
        return;
      }
      if (elements.length != 1) {
        return;
      }
      final PsiElement element = elements[0];
      if (!(element instanceof XmlTag)) {
        return;
      }
      doExtractStyle((XmlTag)element, myTestConfig);
    }
  }

  static class MyTestConfig {
    private final Module myModule;
    private final String myStyleName;
    private final String[] myAttributesToExtract;

    MyTestConfig(@NotNull Module module,
                 @NotNull String styleName,
                 @NotNull String[] attributesToExtract) {
      myModule = module;
      myStyleName = styleName;
      myAttributesToExtract = attributesToExtract;
    }

    @NotNull
    public Module getModule() {
      return myModule;
    }

    @NotNull
    public String getStyleName() {
      return myStyleName;
    }

    @NotNull
    public String[] getAttributesToExtract() {
      return myAttributesToExtract;
    }

    public void validate(@NotNull List<XmlAttribute> extractableAttributes) {
    }
  }
}
