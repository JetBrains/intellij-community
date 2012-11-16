package org.jetbrains.android.refactoring;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.SdkConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.dom.resources.StyleItem;
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
public class AndroidExtractStyleAction extends AndroidBaseLayoutRefactoringAction {
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

  protected boolean isEnabledForTags(@NotNull XmlTag[] tags) {
    return tags.length == 1 && doIsEnabled(tags[0]);
  }

  public static boolean doIsEnabled(@NotNull XmlTag tag) {
    return getLayoutViewElement(tag) != null && getExtractableAttributes(tag).size() > 0;
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
    final ResourceValue parentStyleValue = viewElement.getStyle().getValue();
    final String parentStyle;
    boolean supportImplicitParent = false;

    if (parentStyleValue != null) {
      parentStyle = parentStyleValue.getResourceName();
      if (!ResourceType.STYLE.getName().equals(parentStyleValue.getResourceType()) || parentStyle == null || parentStyle.length() == 0) {
        AndroidUtils.reportError(project, "Invalid parent style reference " + parentStyleValue.toString(), dialogTitle);
        return null;
      }
      supportImplicitParent = parentStyleValue.getPackage() == null;
    }
    else {
      parentStyle = null;
    }

    final String styleName;
    final List<XmlAttribute> styledAttributes;
    final Module chosenModule;
    final boolean searchStyleApplications;

    if (testConfig == null) {
      final ExtractStyleDialog dialog =
        new ExtractStyleDialog(module, fileName, supportImplicitParent ? parentStyle : null, dirNames, extractableAttributes);
      dialog.setTitle(dialogTitle);
      dialog.show();

      if (!dialog.isOK()) {
        return null;
      }
      searchStyleApplications = dialog.isToSearchStyleApplications();
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
      searchStyleApplications = false;
    }
    final boolean[] success = {false};
    final Ref<Style> createdStyleRef = Ref.create();
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
              createdStyleRef.set(style);

              for (XmlAttribute attribute : styledAttributes) {
                if (SdkConstants.NS_RESOURCES.equals(attribute.getNamespace())) {
                  final StyleItem item = style.addItem();
                  item.getName().setStringValue("android:" + attribute.getLocalName());
                  item.setStringValue(attribute.getValue());
                  attributesToDelete.add(attribute);
                }
              }

              if (parentStyleValue != null && (!finalSupportImplicitParent || !styleName.startsWith(parentStyle + "."))) {
                final String aPackage = parentStyleValue.getPackage();
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

    if (!success[0]) {
      return null;
    }

    final Style createdStyle = createdStyleRef.get();
    final XmlTag createdStyleTag = createdStyle != null ? createdStyle.getXmlTag() : null;

    if (createdStyleTag != null) {
      final AndroidFindStyleApplicationsAction.MyStyleData createdStyleData =
        AndroidFindStyleApplicationsAction.getStyleData(createdStyleTag);

      if (createdStyleData != null && searchStyleApplications) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            AndroidFindStyleApplicationsAction.doRefactoringForTag(
              project, createdStyleTag, createdStyleData, file, null);
          }
        });
      }
    }
    return styleName;
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
    if (ArrayUtilRt.find(NON_EXTRACTABLE_ATTRIBUTES, name) >= 0) {
      return false;
    }
    if (name.startsWith(AndroidDomUtil.ATTR_STYLE)) {
      return false;
    }
    return true;
  }

  @Override
  protected void doRefactorForTags(@NotNull Project project, @NotNull XmlTag[] tags) {
    assert tags.length == 1;

    final XmlTag tag = tags[0];
    final Module module = ModuleUtilCore.findModuleForPsiElement(tag);
    assert module != null;
    doExtractStyle(module, tag, true, myTestConfig);
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
