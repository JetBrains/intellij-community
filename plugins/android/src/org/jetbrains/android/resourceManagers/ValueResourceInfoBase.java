package org.jetbrains.android.resourceManagers;

import com.android.resources.ResourceType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
abstract class ValueResourceInfoBase implements ValueResourceInfo {
  protected final String myName;
  protected final ResourceType myType;
  protected final VirtualFile myFile;

  protected ValueResourceInfoBase(@NotNull String name, @NotNull ResourceType type, @NotNull VirtualFile file) {
    myName = name;
    myType = type;
    myFile = file;
  }

  @NotNull
  @Override
  public VirtualFile getContainingFile() {
    return myFile;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public ResourceType getType() {
    return myType;
  }

  @Override
  public String toString() {
    return "ANDROID_RESOURCE: " + myType + ", " + myName + ", " + myFile.getPath() + "]";
  }
}
