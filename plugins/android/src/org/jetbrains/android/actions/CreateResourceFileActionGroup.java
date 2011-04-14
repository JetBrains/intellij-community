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

package org.jetbrains.android.actions;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 21, 2009
 * Time: 3:58:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class CreateResourceFileActionGroup extends DefaultActionGroup {
  private final CreateResourceFileAction myMajorAction;

  public CreateResourceFileActionGroup() {
    CreateResourceFileAction a = new CreateResourceFileAction();
    a.add(new CreateTypedResourceFileAction("Layout", "layout", "LinearLayout"));
    a.add(new CreateTypedResourceFileAction("XML", "xml", "PreferenceScreen"));
    a.add(new CreateTypedResourceFileAction("Drawable", "drawable", "selector"));
    a.add(new CreateTypedResourceFileAction("Values", "values", "resources", true, false));
    a.add(new CreateTypedResourceFileAction("Menu", "menu", "menu", false, false));
    a.add(new CreateTypedResourceFileAction("Animation", "anim", "set"));
    myMajorAction = a;
    add(a);
    for (CreateTypedResourceFileAction subaction : a.getSubactions()) {
      add(subaction);
    }
  }

  @NotNull
  public CreateResourceFileAction getCreateResourceFileAction() {
    return myMajorAction;
  }
}
