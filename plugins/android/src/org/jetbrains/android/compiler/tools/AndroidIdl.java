/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.compiler.tools;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.util.ExecutionUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * IDL compiler.
 *
 * @author Alexey Efimov
 */
public final class AndroidIdl {
  private AndroidIdl() {
  }

  @NotNull
  public static Map<CompilerMessageCategory, List<String>> execute(@NotNull IAndroidTarget target,
                                                                   @NotNull String file,
                                                                   @NotNull String outFile,
                                                                   @NotNull String[] sourceRootPaths) throws IOException {
    List<String> commands = new ArrayList<String>();
    String frameworkAidlPath = target.getPath(IAndroidTarget.ANDROID_AIDL);
    commands.add(target.getPath(IAndroidTarget.AIDL));
    commands.add("-p" + frameworkAidlPath);
    for (String path : sourceRootPaths) {
      commands.add("-I" + path);
    }
    commands.add(file);
    commands.add(outFile);
    return ExecutionUtil.execute(ArrayUtil.toStringArray(commands));
  }

}
