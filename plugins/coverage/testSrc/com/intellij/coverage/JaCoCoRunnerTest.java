// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.execution.configurations.SimpleJavaParameters;
import org.junit.Assert;
import org.junit.Test;

import java.util.regex.Pattern;

public class JaCoCoRunnerTest {
  @Test
  public void excludeIncludePatterns() {
    SimpleJavaParameters javaParameters = new SimpleJavaParameters();
    new JaCoCoCoverageRunner().appendCoverageArgument("a", null, new String[] {"org.*", "com.*"}, javaParameters, true, true, null);
    Assert.assertTrue(Pattern.compile("-javaagent:(.*)jacocoagent.jar=destfile=a,append=false,excludes=org\\.\\*:com\\.\\*").matcher(javaParameters.getVMParametersList().getParametersString()).matches());
  }
}
