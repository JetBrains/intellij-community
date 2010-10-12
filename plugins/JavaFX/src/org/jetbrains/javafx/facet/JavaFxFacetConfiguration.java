package org.jetbrains.javafx.facet;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.sdk.JavaFxSdkType;
import org.jetbrains.javafx.sdk.JavaFxSdkUtil;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxFacetConfiguration implements FacetConfiguration {
  private static final @NonNls String JAVAFX_SDK_ATTR_NAME = "javafx_sdk";

  private Sdk myJavaFxSdk;

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[]{new JavaFxFacetEditorTab(this, editorContext, validatorsManager)};
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    final String s = element.getAttributeValue(JAVAFX_SDK_ATTR_NAME);
    final Sdk sdk = ProjectJdkTable.getInstance().findJdk(s);
    if (sdk != null && sdk.getSdkType() instanceof JavaFxSdkType) {
      setJavaFxSdk(sdk);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (myJavaFxSdk != null) {
      element.setAttribute(JAVAFX_SDK_ATTR_NAME, myJavaFxSdk.getName());
    }
  }

  @Nullable
  public Sdk getJavaFxSdk() {
    return myJavaFxSdk;
  }

  public void setJavaFxSdk(final Sdk javaFxSdk) {
    JavaFxSdkUtil.registerSdkRootListener(javaFxSdk);
    myJavaFxSdk = javaFxSdk;
  }
}
