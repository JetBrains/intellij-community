package org.jetbrains.android.resourceManagers;

import com.android.resources.ResourceType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public interface ValueResourceInfo {
  @Nullable
  XmlAttributeValue computeXmlElement();

  @NotNull
  VirtualFile getContainingFile();

  @NotNull
  String getName();

  @NotNull
  ResourceType getType();

}
