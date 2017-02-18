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

package org.jetbrains.plugins.groovy;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;

import java.util.*;

/**
 * @author ilyas
 */
public class GroovyFileTypeLoader extends FileTypeFactory{

  public static Set<String> getCustomGroovyScriptExtensions() {
    final LinkedHashSet<String> strings = new LinkedHashSet<>();
    strings.add("gdsl");
    strings.add("gy");
    for (GroovyScriptTypeDetector ep : GroovyScriptTypeDetector.EP_NAME.getExtensions()) {
      Collections.addAll(strings, ep.getExtensions());
    }
    return strings;
  }

  public static List<String> getAllGroovyExtensions() {
    final ArrayList<String> strings = new ArrayList<>();
    strings.add(GroovyFileType.DEFAULT_EXTENSION);
    strings.addAll(getCustomGroovyScriptExtensions());
    return strings;
  }

  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(GroovyFileType.GROOVY_FILE_TYPE, StringUtil.join(getAllGroovyExtensions(), ";"));
  }
}
