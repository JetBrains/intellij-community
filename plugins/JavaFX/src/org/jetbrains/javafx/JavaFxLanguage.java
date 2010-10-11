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
package org.jetbrains.javafx;

import com.intellij.lang.Language;
import com.intellij.util.containers.HashSet;
import org.jetbrains.javafx.lang.validation.*;

import java.util.Set;

/**
 * Descriptor of JavaFx Script language
 *
 * @author andrey, Alexey.Ivanov
 */
public class JavaFxLanguage extends Language {
  public static final JavaFxLanguage INSTANCE = new JavaFxLanguage();
  private final Set<Class<? extends JavaFxAnnotatingVisitor>> myAnnotators = new HashSet<Class<? extends JavaFxAnnotatingVisitor>>();

  {
    myAnnotators.add(LiteralAnnotatingVisitor.class);
    myAnnotators.add(BreakContinueAnnotatingVisitor.class);
    myAnnotators.add(JavaFxDeprecationVisitor.class);
    myAnnotators.add(UnresolvedReferenceVisitor.class);
  }

  public static JavaFxLanguage getInstance() {
    return INSTANCE;
  }

  private JavaFxLanguage() {
    super("JavaFx");
  }

  public Set<Class<? extends JavaFxAnnotatingVisitor>> getAnnotators() {
    return myAnnotators;
  }

  @Override
  public boolean isCaseSensitive() {
    return true;
  }
}
