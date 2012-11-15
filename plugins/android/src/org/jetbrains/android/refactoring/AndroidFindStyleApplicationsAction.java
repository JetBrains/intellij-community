package org.jetbrains.android.refactoring;

import com.android.resources.ResourceType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.resources.ResourcesDomFileDescription;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.ValueResourceInfoImpl;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.ErrorReporter;
import org.jetbrains.android.util.ProjectBasedErrorReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFindStyleApplicationsAction extends AndroidBaseXmlRefactoringAction {

  private final MyTestConfig myTestConfig;

  public AndroidFindStyleApplicationsAction() {
    this(null);
  }

  public AndroidFindStyleApplicationsAction(@Nullable MyTestConfig testConfig) {
    myTestConfig = testConfig;
  }

  @Override
  protected boolean isEnabledForTags(@NotNull XmlTag[] tags) {
    if (tags.length != 1) {
      return false;
    }
    final MyStyleData data = getStyleData(tags[0]);
    return data != null && data.getStyle().getItems().size() > 0;
  }

  @Override
  protected boolean isMyFile(PsiFile file) {
    return DomManager.getDomManager(file.getProject()).getDomFileDescription(
      (XmlFile)file) instanceof ResourcesDomFileDescription;
  }

  @Override
  protected void doRefactorForTags(@NotNull Project project, @NotNull XmlTag[] tags) {
    assert tags.length == 1;
    final XmlTag tag = tags[0];

    final MyStyleData styleData = getStyleData(tag);
    assert styleData != null;

    doRefactoringForTag(project, tag, styleData, null, myTestConfig);
  }

  static void doRefactoringForTag(Project project, XmlTag tag, MyStyleData styleData, PsiFile context, MyTestConfig testConfig) {
    final ErrorReporter errorReporter = new ProjectBasedErrorReporter(project);
    final Style style = styleData.getStyle();
    final Map<AndroidAttributeInfo, String> attrMap =
      AndroidRefactoringUtil.computeAttributeMap(style, new ProjectBasedErrorReporter(project),
                                                 AndroidBundle.message("android.find.style.applications.title"));

    if (attrMap == null || attrMap.size() == 0) {
      return;
    }
    final AndroidFacet facet = styleData.getFacet();
    final Module module = facet.getModule();
    final StyleRefData parentStyleRef = AndroidRefactoringUtil.getParentStyle(style);
    PsiElement parentStyleAttrName = null;

    if (parentStyleRef != null) {
      parentStyleAttrName = resolveStyleRef(parentStyleRef, facet);

      if (parentStyleAttrName == null) {
        errorReporter.report("Cannot resolve parent style '" + parentStyleRef.getStyleName() + "'",
                             AndroidBundle.message("android.find.style.applications.title"));
        return;
      }
    }
    final AndroidFindStyleApplicationsProcessor processor = new AndroidFindStyleApplicationsProcessor(
      module, attrMap, styleData.getName(), tag, styleData.getNameAttrValue(), parentStyleAttrName, context);
    final VirtualFile contextVFile = context != null ? context.getVirtualFile() : null;

    if (testConfig != null) {
      processor.configureScope(testConfig.getScope(), contextVFile);
      processor.run();
      return;
    }
    processor.setPreviewUsages(true);

    final boolean showModuleRadio = AndroidFindStyleApplicationsProcessor.getAllModulesToScan(module).size() > 1;

    if (showModuleRadio || contextVFile != null) {
      final AndroidFindStyleApplicationsDialog dialog = new AndroidFindStyleApplicationsDialog(
        contextVFile, processor, showModuleRadio);
      dialog.show();
    }
    else {
      processor.run();
    }
  }

  private static PsiElement resolveStyleRef(StyleRefData styleRef, AndroidFacet facet) {
    final ResourceManager resourceManager = facet.getResourceManager(styleRef.getStylePackage());
    
    if (resourceManager == null) {
      return null;
    }
    final List<ValueResourceInfoImpl> infos = resourceManager.findValueResourceInfos(
      ResourceType.STYLE.getName(), styleRef.getStyleName(), true, false);
    return infos.size() == 1 ? infos.get(0).computeXmlElement() : null;
  }

  @Nullable
  static MyStyleData getStyleData(@NotNull XmlTag tag) {
    final DomElement element = DomManager.getDomManager(tag.getProject()).getDomElement(tag);    

    if (!(element instanceof Style)) {
      return null;
    }
    final Style style = (Style)element;
    final GenericAttributeValue<String> styleNameDomAttr = style.getName();
    final String styleName = styleNameDomAttr.getStringValue();
    final XmlAttributeValue styleNameAttrValue = styleNameDomAttr.getXmlAttributeValue();

    if (styleName == null ||
        styleName.length() == 0 ||
        styleNameAttrValue == null) {
      return null;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(tag);

    if (facet == null) {
      return null;
    }
    return new MyStyleData(style, styleName, facet, styleNameAttrValue);
  }
  
  static class MyStyleData {
    private final Style myStyle;
    private final String myName;
    private final XmlAttributeValue myNameAttrValue;
    private final AndroidFacet myFacet;

    private MyStyleData(@NotNull Style style,
                        @NotNull String name,
                        @NotNull AndroidFacet facet,
                        @NotNull XmlAttributeValue nameAttrValue) {
      myStyle = style;
      myName = name;
      myFacet = facet;
      myNameAttrValue = nameAttrValue;
    }

    @NotNull
    public Style getStyle() {
      return myStyle;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @NotNull
    public AndroidFacet getFacet() {
      return myFacet;
    }

    @NotNull
    public XmlAttributeValue getNameAttrValue() {
      return myNameAttrValue;
    }
  }

  static class MyTestConfig {
    private final AndroidFindStyleApplicationsProcessor.MyScope myScope;

    MyTestConfig(@NotNull AndroidFindStyleApplicationsProcessor.MyScope scope) {
      myScope = scope;
    }

    @NotNull
    public AndroidFindStyleApplicationsProcessor.MyScope getScope() {
      return myScope;
    }
  }
}
