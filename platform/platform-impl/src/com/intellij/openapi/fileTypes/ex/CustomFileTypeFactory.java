package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.UserFileType;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

public interface CustomFileTypeFactory {
  ExtensionPointName<CustomFileTypeFactory> EP_NAME = ExtensionPointName.create("com.intellij.customFileTypeFactory");

  @Nullable
  UserFileType createFileType(Element element);
}