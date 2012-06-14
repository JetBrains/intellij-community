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

import com.android.resources.ResourceFolderType;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.android.dom.animation.AndroidAnimationUtils;
import org.jetbrains.android.dom.animator.AndroidAnimatorUtil;
import org.jetbrains.android.dom.drawable.AndroidDrawableDomUtil;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    a.add(new AndroidCreateLayoutFileAction());

    a.add(new CreateTypedResourceFileAction("XML", ResourceFolderType.XML, false, true) {
      @NotNull
      @Override
      public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
        return AndroidXmlResourcesUtil.getPossibleRoots(facet);
      }
    });

    a.add(new CreateTypedResourceFileAction("Drawable", ResourceFolderType.DRAWABLE, false, true) {
      @NotNull
      @Override
      public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
        return AndroidDrawableDomUtil.getPossibleRoots();
      }
    });

    a.add(new CreateTypedResourceFileAction("Color", ResourceFolderType.COLOR, false, false));
    a.add(new CreateTypedResourceFileAction("Values", ResourceFolderType.VALUES, true, false));
    a.add(new CreateTypedResourceFileAction("Menu", ResourceFolderType.MENU, false, false));

    a.add(new CreateTypedResourceFileAction("Animation", ResourceFolderType.ANIM, false, true) {
      @NotNull
      @Override
      public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
        return AndroidAnimationUtils.getPossibleChildren(facet);
      }
    });

    a.add(new CreateTypedResourceFileAction("Animator", ResourceFolderType.ANIMATOR, false, true) {
      @NotNull
      @Override
      public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
        return AndroidAnimatorUtil.getPossibleChildren();
      }
    });

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
