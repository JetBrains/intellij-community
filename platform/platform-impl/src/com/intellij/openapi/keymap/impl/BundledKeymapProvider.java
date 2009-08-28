package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.List;

/**
 * @author yole
 */
public interface BundledKeymapProvider {
  ExtensionPointName<BundledKeymapProvider> EP_NAME = ExtensionPointName.create("com.intellij.bundledKeymapProvider");

  List<String> getKeymapFileNames();
}
