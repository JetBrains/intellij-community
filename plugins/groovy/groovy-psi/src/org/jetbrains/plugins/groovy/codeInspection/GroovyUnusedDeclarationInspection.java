// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;

public class GroovyUnusedDeclarationInspection extends LocalInspectionTool implements UnfairLocalInspectionTool {

  public static final String SHORT_NAME = "GroovyUnusedDeclaration";
}
