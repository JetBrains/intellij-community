/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.groovy;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.lang.UrlClassLoader;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.ExternalProcessUtil;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author peter
 */
class ForkedGroovyc implements GroovycFlavor {
  private final boolean myOptimizeClassLoading;
  private final ModuleChunk myChunk;

  ForkedGroovyc(boolean optimizeClassLoading, ModuleChunk chunk) {
    myOptimizeClassLoading = optimizeClassLoading;
    myChunk = chunk;
  }

  @Override
  public GroovycContinuation runGroovyc(Collection<String> compilationClassPath,
                                        boolean forStubs,
                                        JpsGroovySettings settings,
                                        File tempFile,
                                        final GroovycOutputParser parser)
    throws Exception {
    List<String> classpath = new ArrayList<>();
    if (myOptimizeClassLoading) {
      classpath.addAll(GroovyBuilder.getGroovyRtRoots());
      classpath.add(ClasspathBootstrap.getResourcePath(Function.class));
      classpath.add(ClasspathBootstrap.getResourcePath(UrlClassLoader.class));
      classpath.add(ClasspathBootstrap.getResourceFile(THashMap.class).getPath());
    } else {
      classpath.addAll(compilationClassPath);
    }

    List<String> vmParams = ContainerUtilRt.newArrayList();
    vmParams.add("-Xmx" + System.getProperty("groovyc.heap.size", settings.heapSize) + "m");
    vmParams.add("-Dfile.encoding=" + System.getProperty("file.encoding"));
    //vmParams.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239");
    
    if ("false".equals(System.getProperty(GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY))) {
      vmParams.add("-D" + GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY + "=false");
    }
    String configScript = settings.configScript;
    if (StringUtil.isNotEmpty(configScript)) {
      vmParams.add("-D" + GroovyRtConstants.GROOVYC_CONFIG_SCRIPT + "=" + configScript);
    }

    String grapeRoot = System.getProperty(GroovycOutputParser.GRAPE_ROOT);
    if (grapeRoot != null) {
      vmParams.add("-D" + GroovycOutputParser.GRAPE_ROOT + "=" + grapeRoot);
    }

    final List<String> cmd = ExternalProcessUtil.buildJavaCommandLine(
      getJavaExecutable(myChunk),
      "org.jetbrains.groovy.compiler.rt.GroovycRunner",
      Collections.emptyList(), classpath,
      vmParams,
      getProgramParams(tempFile, settings, forStubs)
    );
    final Process process = Runtime.getRuntime().exec(ArrayUtil.toStringArray(cmd));
    ProcessHandler handler = new BaseOSProcessHandler(process, StringUtil.join(cmd, " "), null) {
      @NotNull
      @Override
      protected Future<?> executeOnPooledThread(@NotNull Runnable task) {
        return SharedThreadPool.getInstance().executeOnPooledThread(task);
      }

      @Override
      public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
        parser.notifyTextAvailable(text, outputType);
      }
    };

    handler.startNotify();
    handler.waitFor();
    parser.notifyFinished(process.exitValue());
    return null;
  }

  private List<String> getProgramParams(File tempFile, JpsGroovySettings settings, boolean forStubs) {
    List<String> programParams = ContainerUtilRt.newArrayList(myOptimizeClassLoading ? GroovyRtConstants.OPTIMIZE : "do_not_optimize",
                                                              forStubs ? "stubs" : "groovyc",
                                                              tempFile.getPath());
    if (settings.invokeDynamic) {
      programParams.add("--indy");
    }
    return programParams;
  }


  private static String getJavaExecutable(ModuleChunk chunk) {
    JpsSdk<?> sdk = GroovyBuilder.getJdk(chunk);
    return sdk != null ? JpsJavaSdkType.getJavaExecutable(sdk) : SystemProperties.getJavaHome() + "/bin/java";
  }

}
