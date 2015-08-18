/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import com.intellij.ide.ui.search.ConfigurableHit;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LightColors;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ui.UIUtil;

import javax.swing.event.DocumentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;

abstract class SettingsFilter extends ElementFilter.Active.Impl<SimpleNode> {
  final OptionsEditorContext myContext = new OptionsEditorContext(this);
  final Project myProject;

  boolean myDocumentWasChanged;

  private final SearchTextField mySearch;
  private final ConfigurableGroup[] myGroups;

  private SearchableOptionsRegistrar myRegistrar = SearchableOptionsRegistrar.getInstance();
  private Set<Configurable> myFiltered;
  private ConfigurableHit myHits;

  private boolean myUpdateRejected;
  private Configurable myLastSelected;

  SettingsFilter(Project project, ConfigurableGroup[] groups, SearchTextField search) {
    myProject = project;
    myGroups = groups;
    mySearch = search;
    mySearch.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent event) {
        update(event.getType(), true, false);
        // request focus if needed on changing the filter text
        IdeFocusManager manager = IdeFocusManager.findInstanceByComponent(mySearch);
        if (manager.getFocusedDescendantFor(mySearch) == null) {
          manager.requestFocus(mySearch, true);
        }
      }
    });
    mySearch.getTextEditor().addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        if (!mySearch.getText().isEmpty()) {
          if (!myContext.isHoldingFilter()) {
            setHoldingFilter(true);
          }
          if (!mySearch.getTextEditor().isFocusOwner()) {
            mySearch.selectText();
          }
        }
      }
    });
  }

  abstract Configurable getConfigurable(SimpleNode node);

  abstract SimpleNode findNode(Configurable configurable);

  abstract void updateSpotlight(boolean now);

  @Override
  public boolean shouldBeShowing(SimpleNode node) {
    if (myFiltered != null) {
      Configurable configurable = getConfigurable(node);
      if (configurable != null) {
        if (!myFiltered.contains(configurable)) {
          if (myHits != null) {
            Set<Configurable> configurables = myHits.getNameFullHits();
            while (node != null) {
              if (configurable != null) {
                if (configurables.contains(configurable)) {
                  return true;
                }
              }
              node = node.getParent();
              configurable = getConfigurable(node);
            }
          }
          return false;
        }
      }
    }
    return true;
  }

  String getFilterText() {
    String text = mySearch.getText();
    return text == null ? "" : text.trim();
  }

  void setHoldingFilter(boolean holding) {
    myContext.setHoldingFilter(holding);
    updateSpotlight(false);
  }

  boolean contains(Configurable configurable) {
    return myHits != null && myHits.getNameHits().contains(configurable);
  }

  ActionCallback update(boolean adjustSelection, boolean now) {
    return update(DocumentEvent.EventType.CHANGE, adjustSelection, now);
  }

  ActionCallback update(String text, boolean adjustSelection, boolean now) {
    try {
      myUpdateRejected = true;
      mySearch.setText(text);
    }
    finally {
      myUpdateRejected = false;
    }
    return update(adjustSelection, now);
  }

  private ActionCallback update(DocumentEvent.EventType type, boolean adjustSelection, boolean now) {
    if (myUpdateRejected) {
      return ActionCallback.REJECTED;
    }
    String text = getFilterText();
    if (text.isEmpty()) {
      myContext.setHoldingFilter(false);
      myFiltered = null;
    }
    else {
      myContext.setHoldingFilter(true);
      myHits = myRegistrar.getConfigurables(myGroups, type, myFiltered, text, myProject);
      myFiltered = myHits.getAll();
    }
    mySearch.getTextEditor().setBackground(myFiltered != null && myFiltered.isEmpty()
                                           ? LightColors.RED
                                           : UIUtil.getTextFieldBackground());


    Configurable current = myContext.getCurrentConfigurable();

    boolean shouldMoveSelection = myHits == null || (
      !myHits.getNameFullHits().contains(current) &&
      !myHits.getContentHits().contains(current));

    if (shouldMoveSelection && type != DocumentEvent.EventType.INSERT && (myFiltered == null || myFiltered.contains(current))) {
      shouldMoveSelection = false;
    }

    Configurable candidate = adjustSelection ? current : null;
    if (shouldMoveSelection && myHits != null) {
      if (!myHits.getNameHits().isEmpty()) {
        candidate = findConfigurable(myHits.getNameHits(), myHits.getNameFullHits());
      }
      else if (!myHits.getContentHits().isEmpty()) {
        candidate = findConfigurable(myHits.getContentHits(), null);
      }
    }
    updateSpotlight(false);

    if ((myFiltered == null || !myFiltered.isEmpty()) && candidate == null && myLastSelected != null) {
      candidate = myLastSelected;
      myLastSelected = null;
    }
    if (candidate == null && current != null) {
      myLastSelected = current;
    }
    SimpleNode node = !adjustSelection ? null : findNode(candidate);
    ActionCallback callback = fireUpdate(node, adjustSelection, now);
    myDocumentWasChanged = true;
    return callback;
  }

  private static Configurable findConfigurable(Set<Configurable> configurables, Set<Configurable> hits) {
    Configurable candidate = null;
    for (Configurable configurable : configurables) {
      if (hits != null && hits.contains(configurable)) {
        return configurable;
      }
      if (candidate == null && !isEmptyParent(configurable)) {
        candidate = configurable;
      }
    }
    return candidate;
  }

  private static boolean isEmptyParent(Configurable configurable) {
    SearchableConfigurable.Parent parent = ConfigurableWrapper.cast(SearchableConfigurable.Parent.class, configurable);
    return parent != null && !parent.hasOwnContent();
  }
}
