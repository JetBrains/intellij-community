// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xml.SubTagList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomAntlib extends AntDomElement{

  public static final String ANTLIB_DEFAULT_FILENAME = "antlib.xml";
  public static final String ANTLIB_URI_PREFIX = "antlib:";

  @SubTagList("typedef")
  public abstract List<AntDomTypeDef> getTypedefs();

  @SubTagList("taskdef")
  public abstract List<AntDomTypeDef> getTaskdefs();

  @SubTagList("macrodef")
  public abstract List<AntDomTypeDef> getMacrodefs();

  @SubTagList("presetdef")
  public abstract List<AntDomTypeDef> getPresetdefs();

  @SubTagList("scriptdef")
  public abstract List<AntDomTypeDef> getScriptdefs();

  public static @Nullable @NlsSafe String toAntlibResource(String antlibUri) {
    if (!antlibUri.startsWith(ANTLIB_URI_PREFIX)) {
      return null;
    }
    return antlibUri.substring(ANTLIB_URI_PREFIX.length()).replace('.', '/') + "/" + ANTLIB_DEFAULT_FILENAME;
  }
}
