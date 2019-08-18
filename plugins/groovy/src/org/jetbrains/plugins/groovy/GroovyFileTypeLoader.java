// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyFileTypeLoader extends FileTypeFactory {

  public static Set<String> getCustomGroovyScriptExtensions() {
    final LinkedHashSet<String> strings = new LinkedHashSet<>();
    strings.add("gdsl");
    strings.add("gy");
    for (GroovyScriptTypeDetector ep : GroovyScriptTypeDetector.EP_NAME.getExtensions()) {
      Collections.addAll(strings, ep.getExtensions());
    }
    return strings;
  }

  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(GroovyFileType.GROOVY_FILE_TYPE, StringUtil.join(getCustomGroovyScriptExtensions(), ";"));
  }
}
