package org.jetbrains.plugins.javaFX;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

/**
 * User: anna
 * Date: 3/13/13
 */
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
    myState.setWidth(state.myWidth);
    myState.setHeight(state.myHeight);
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
    private String myWidth = "600";
    private String myHeight = "400";

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
  }
}
