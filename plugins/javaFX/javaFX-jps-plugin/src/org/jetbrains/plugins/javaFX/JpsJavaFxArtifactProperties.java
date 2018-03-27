package org.jetbrains.plugins.javaFX;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.plugins.javaFX.packaging.JavaFxApplicationIcons;
import org.jetbrains.plugins.javaFX.packaging.JavaFxManifestAttribute;
import org.jetbrains.plugins.javaFX.packaging.JavaFxPackagerConstants;

import java.util.ArrayList;
import java.util.List;

public class JpsJavaFxArtifactProperties extends JpsElementBase<JpsJavaFxArtifactProperties> {
  protected MyState myState = new MyState();

  public JpsJavaFxArtifactProperties() {
  }

  public JpsJavaFxArtifactProperties(MyState state) {
    copyState(state);
  }

  private void copyState(MyState state) {
    myState.setAppClass(state.myAppClass);
    myState.setTitle(state.myTitle);
    myState.setVendor(state.myVendor);
    myState.setDescription(state.myDescription);
    myState.setVersion(state.myVersion);
    myState.setWidth(state.myWidth);
    myState.setHeight(state.myHeight);
    myState.setHtmlTemplateFile(state.myHtmlTemplateFile);
    myState.setHtmlPlaceholderId(state.myHtmlPlaceholderId);
    myState.setHtmlParamFile(state.myHtmlParamFile);
    myState.setParamFile(state.myParamFile);
    myState.setUpdateMode(state.myUpdateMode);
    myState.setEnabledSigning(state.myEnabledSigning);
    myState.setSelfSigning(state.mySelfSigning);
    myState.setKeystore(state.myKeystore);
    myState.setKeypass(state.myKeypass);
    myState.setStorepass(state.myStorepass);
    myState.setAlias(state.myAlias);
    myState.setConvertCss2Bin(state.myConvertCss2Bin);
    myState.setNativeBundle(state.myNativeBundle);
    myState.setCustomManifestAttributes(state.myCustomManifestAttributes);
    myState.setIcons(state.myIcons);
    myState.setMsgOutputLevel(state.myMsgOutputLevel);
  }

  @NotNull
  @Override
  public JpsJavaFxArtifactProperties createCopy() {
    return new JpsJavaFxArtifactProperties(myState);
  }

  @Override
  public void applyChanges(@NotNull JpsJavaFxArtifactProperties modified) {
    copyState(modified.myState);
  }

  public static class MyState {
    private String myTitle;
    private String myVendor;
    private String myDescription;
    private String myAppClass;
    private String myVersion;
    private String myWidth = JavaFxPackagerConstants.DEFAULT_WEIGHT;
    private String myHeight = JavaFxPackagerConstants.DEFAULT_HEIGHT;
    private String myHtmlTemplateFile;
    private String myHtmlPlaceholderId;
    private String myHtmlParamFile;
    private String myParamFile;
    private String myUpdateMode = JavaFxPackagerConstants.UPDATE_MODE_BACKGROUND;
    private boolean myEnabledSigning = false;
    private boolean mySelfSigning = true;
    private String myAlias;
    private String myKeystore;
    private String myStorepass;
    private String myKeypass;
    private boolean myConvertCss2Bin;
    public JavaFxPackagerConstants.NativeBundles myNativeBundle = JavaFxPackagerConstants.NativeBundles.none;
    private List<JavaFxManifestAttribute> myCustomManifestAttributes = new ArrayList<>();
    private JavaFxApplicationIcons myIcons = new JavaFxApplicationIcons();
    private JavaFxPackagerConstants.MsgOutputLevel myMsgOutputLevel = JavaFxPackagerConstants.MsgOutputLevel.Default;

    public String getTitle() {
      return myTitle;
    }

    public void setTitle(String title) {
      myTitle = title;
    }

    public String getVendor() {
      return myVendor;
    }

    public void setVendor(String vendor) {
      myVendor = vendor;
    }

    public String getDescription() {
      return myDescription;
    }

    public void setDescription(String description) {
      myDescription = description;
    }

    public String getVersion() {
      return myVersion;
    }

    public void setVersion(String version) {
      myVersion = version;
    }

    public JavaFxApplicationIcons getIcons() {
      return myIcons;
    }

    public void setIcons(JavaFxApplicationIcons icons) {
      myIcons = icons;
    }

    public String getAppClass() {
      return myAppClass;
    }

    public void setAppClass(String appClass) {
      myAppClass = appClass;
    }

    public String getWidth() {
      return myWidth;
    }

    public String getHeight() {
      return myHeight;
    }

    public void setWidth(String width) {
      myWidth = width;
    }

    public void setHeight(String height) {
      myHeight = height;
    }

    public String getHtmlTemplateFile() {
      return myHtmlTemplateFile;
    }

    public void setHtmlTemplateFile(String htmlTemplateFile) {
      myHtmlTemplateFile = htmlTemplateFile;
    }

    public String getHtmlPlaceholderId() {
      return myHtmlPlaceholderId;
    }

    public void setHtmlPlaceholderId(String htmlPlaceholderId) {
      myHtmlPlaceholderId = htmlPlaceholderId;
    }

    public String getHtmlParamFile() {
      return myHtmlParamFile;
    }

    public String getParamFile() {
      return myParamFile;
    }

    public void setHtmlParamFile(String htmlParamFile) {
      myHtmlParamFile = htmlParamFile;
    }

    public void setParamFile(String paramFile) {
      myParamFile = paramFile;
    }

    public String getUpdateMode() {
      return myUpdateMode;
    }

    public void setUpdateMode(String updateMode) {
      myUpdateMode = updateMode;
    }

    public boolean isEnabledSigning() {
      return myEnabledSigning;
    }

    public void setEnabledSigning(boolean enabledSigning) {
      myEnabledSigning = enabledSigning;
    }

    public boolean isSelfSigning() {
      return mySelfSigning;
    }

    public void setSelfSigning(boolean selfSigning) {
      mySelfSigning = selfSigning;
    }

    public String getAlias() {
      return myAlias;
    }

    public void setAlias(String alias) {
      myAlias = alias;
    }

    public String getKeystore() {
      return myKeystore;
    }

    public void setKeystore(String keystore) {
      myKeystore = keystore;
    }

    public String getStorepass() {
      return myStorepass;
    }

    public void setStorepass(String storepass) {
      myStorepass = storepass;
    }

    public String getKeypass() {
      return myKeypass;
    }

    public void setKeypass(String keypass) {
      myKeypass = keypass;
    }

    public boolean isConvertCss2Bin() {
      return myConvertCss2Bin;
    }

    public void setConvertCss2Bin(boolean convertCss2Bin) {
      myConvertCss2Bin = convertCss2Bin;
    }

    public JavaFxPackagerConstants.NativeBundles getNativeBundle() {
      return myNativeBundle;
    }

    public void setNativeBundle(JavaFxPackagerConstants.NativeBundles nativeBundle) {
      myNativeBundle = nativeBundle;
    }

    public List<JavaFxManifestAttribute> getCustomManifestAttributes() {
      return myCustomManifestAttributes;
    }

    public void setCustomManifestAttributes(List<JavaFxManifestAttribute> customManifestAttributes) {
      myCustomManifestAttributes = customManifestAttributes;
    }

    public JavaFxPackagerConstants.MsgOutputLevel getMsgOutputLevel() {
      return myMsgOutputLevel;
    }

    public void setMsgOutputLevel(JavaFxPackagerConstants.MsgOutputLevel msgOutputLevel) {
      myMsgOutputLevel = msgOutputLevel;
    }
  }
}
