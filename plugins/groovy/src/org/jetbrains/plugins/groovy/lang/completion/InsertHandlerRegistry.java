/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.handlers.ContextSpecificInsertHandler;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class InsertHandlerRegistry implements ApplicationComponent {

  private ArrayList<ContextSpecificInsertHandler> myHandlers = new ArrayList<ContextSpecificInsertHandler>();

  @NonNls
  @NotNull
  public String getComponentName() {
    return "InsertHandlerRegistry";
  }

  public static InsertHandlerRegistry getInstance() {
    return ApplicationManager.getApplication().getComponent(InsertHandlerRegistry.class);
  }

  public void registerSpecificInsertHandler(ContextSpecificInsertHandler handler) {
    myHandlers.add(handler);
  }

  public ContextSpecificInsertHandler[] getSpecificInsertHandlers(){
    return myHandlers.toArray(new ContextSpecificInsertHandler[myHandlers.size()]);
  }


  public void initComponent() {

  }

  public void disposeComponent() {

  }
}
