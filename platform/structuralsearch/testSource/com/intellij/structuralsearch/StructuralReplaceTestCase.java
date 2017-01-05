/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: maxim.mossienko
 * Date: Oct 11, 2005
 * Time: 10:10:48 PM
 * To change this template use File | Settings | File Templates.
 */
abstract class StructuralReplaceTestCase extends LightQuickFixTestCase {
  protected Replacer replacer;
  protected ReplaceOptions options;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    StructuralSearchUtil.ourUseUniversalMatchingAlgorithm = false;

    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_4);

    options = new ReplaceOptions();
    options.setMatchOptions(new MatchOptions());
    replacer = new Replacer(getProject(), null);
  }

  protected String loadFile(String fileName) throws IOException {
    return FileUtilRt.loadFile(new File(getTestDataPath() + FileUtilRt.getExtension(fileName) + "/" + fileName), CharsetToolkit.UTF8, true);
  }
}
