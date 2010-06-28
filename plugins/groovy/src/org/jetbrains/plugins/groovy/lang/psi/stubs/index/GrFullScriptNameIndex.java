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
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.IntStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;

import java.util.Collection;

/**
 * @author ilyas
 */
public class GrFullScriptNameIndex extends IntStubIndexExtension<GroovyFile> {
  public static final StubIndexKey<Integer, GroovyFile> KEY = StubIndexKey.createIndexKey("gr.script.fqn");

  private static final GrFullScriptNameIndex ourInstance = new GrFullScriptNameIndex();
  public static GrFullScriptNameIndex getInstance() {
    return ourInstance;
  }

  public StubIndexKey<Integer, GroovyFile> getKey() {
    return KEY;
  }

  public Collection<GroovyFile> get(final Integer integer, final Project project, final GlobalSearchScope scope) {
    return super.get(integer, project, new GrSourceFilterScope(scope));
  }
}