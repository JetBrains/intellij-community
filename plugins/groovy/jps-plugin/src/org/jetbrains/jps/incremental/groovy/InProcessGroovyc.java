/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
class InProcessGroovyc {
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  static void runGroovycInThisProcess(Collection<String> compilationClassPath,
                                      List<String> programParams,
                                      GroovycOutputParser parser)
    throws MalformedURLException {
    List<URL> urls = ContainerUtil.newArrayList();
    for (String s : compilationClassPath) {
      urls.add(new File(s).toURI().toURL());
    }
    UrlClassLoader loader = UrlClassLoader.build().urls(urls).useCache().get();

    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
    
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    
    System.setOut(new PrintStream(out));
    System.setErr(new PrintStream(err));
    Thread.currentThread().setContextClassLoader(loader);
    try {
      Class<?> runnerClass = loader.loadClass("org.jetbrains.groovy.compiler.rt.GroovycRunner");
      Method intMain = runnerClass.getDeclaredMethod("intMain", String[].class);
      Integer exitCode = (Integer)intMain.invoke(null, new Object[]{ArrayUtil.toStringArray(programParams)});
      parser.notifyFinished(exitCode);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
      Thread.currentThread().setContextClassLoader(oldLoader);
    }

    for (String line : StringUtil.splitByLines(out.toString())) {
      parser.notifyTextAvailable(line, ProcessOutputTypes.STDOUT);
    }
    for (String line : StringUtil.splitByLines(err.toString())) {
      parser.notifyTextAvailable(line, ProcessOutputTypes.STDERR);
    }
  }
}
