/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeEP;

import java.util.*;

/**
 * @author ilyas
 */
public class GroovyFileTypeLoader extends FileTypeFactory{
  public static final List<FileType> GROOVY_FILE_TYPES = new ArrayList<FileType>();

  public static FileType[] getGroovyEnabledFileTypes() {
    return GROOVY_FILE_TYPES.toArray(new FileType[GROOVY_FILE_TYPES.size()]);
  }

  public static Set<String> getCustomGroovyScriptExtensions() {
    final LinkedHashSet<String> strings = new LinkedHashSet<String>();
    strings.add("gdsl");
    strings.add("gpp");
    strings.add("grunit");
    for (GroovyScriptTypeEP ep : GroovyScriptType.EP_NAME.getExtensions()) {
      ContainerUtil.addAll(strings, ep.extensions.split(";"));
    }
    return strings;
  }

  public static List<String> getAllGroovyExtensions() {
    final ArrayList<String> strings = new ArrayList<String>();
    strings.add(GroovyFileType.DEFAULT_EXTENSION);
    strings.addAll(getCustomGroovyScriptExtensions());
    return strings;
  }

  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(GroovyFileType.GROOVY_FILE_TYPE, StringUtil.join(getAllGroovyExtensions(), ";"));
    GROOVY_FILE_TYPES.add(GroovyFileType.GROOVY_FILE_TYPE);
  }
}
