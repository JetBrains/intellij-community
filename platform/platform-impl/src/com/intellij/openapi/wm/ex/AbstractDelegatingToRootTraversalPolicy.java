/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.wm.ex;

import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 1/14/13 3:03 PM
 */
public class AbstractDelegatingToRootTraversalPolicy extends FocusTraversalPolicy {

  @Override
  public Component getComponentAfter(final Container aContainer, final Component aComponent) {
    final Container cycleRootAncestor = aContainer.getFocusCycleRootAncestor();
    return cycleRootAncestor.getFocusTraversalPolicy().getComponentAfter(cycleRootAncestor, aContainer);
  }

  @Override
  public Component getComponentBefore(final Container aContainer, final Component aComponent) {
    final Container cycleRootAncestor = aContainer.getFocusCycleRootAncestor();
    return cycleRootAncestor.getFocusTraversalPolicy().getComponentBefore(cycleRootAncestor, aContainer);
  }

  @Override
  public Component getFirstComponent(final Container aContainer) {
    final Container cycleRootAncestor = aContainer.getFocusCycleRootAncestor();
    return cycleRootAncestor.getFocusTraversalPolicy().getFirstComponent(cycleRootAncestor);
  }

  @Override
  public Component getLastComponent(final Container aContainer) {
    final Container cycleRootAncestor = aContainer.getFocusCycleRootAncestor();
    return cycleRootAncestor.getFocusTraversalPolicy().getLastComponent(cycleRootAncestor);
  }

  @Override
  public Component getDefaultComponent(Container aContainer) {
    return aContainer;
  }
}
