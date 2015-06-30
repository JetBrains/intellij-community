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
package com.jetbrains.reactiveidea;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.reactivemodel.*;
import com.jetbrains.reactivemodel.models.AbsentModel;
import com.jetbrains.reactivemodel.models.MapModel;
import com.jetbrains.reactivemodel.models.PrimitiveModel;
import com.jetbrains.reactivemodel.util.Lifetime;
import com.jetbrains.reactivemodel.util.LifetimeDefinition;
import kotlin.Function2;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;

public class ModelListPopup extends ListPopupImpl {
  private final LifetimeDefinition myLifetime;
  private ReactiveModel myModel;
  private Path myPath;
  private int mySelectedIndex = 0;

  @NotNull
  @Override
  protected MyList createList() {
    return new MyList(myListModel) {

      //fireSelectionValueChanged

      @NotNull
      @Override
      public Object[] getSelectedValues() {
        return new Object[]{getSelectedValue()};
      }

      @Override
      public int getSelectedIndex() {
        return mySelectedIndex;
      }

      @Override
      public Object getSelectedValue() {
        return ((ListPopupStep)myStep).getValues().get(getSelectedIndex());
      }

      @Override
      public void setSelectedIndex(final int index) {
        mySelectedIndex = index;
        if (myModel != null) {
          setSelectedIndexImpl(index);
        }
      }

      @Override
      public void setSelectedValue(Object anObject, boolean shouldScroll) {
        super.setSelectedValue(anObject, shouldScroll);
      }
    };
  }

  private void setSelectedIndexImpl(final int index) {
    myModel.transaction(new Function1<MapModel, MapModel>() {
      @Override
      public MapModel invoke(MapModel mapModel) {
        return (MapModel)ReactivemodelPackage.putIn(myPath.div("selectedIndex"), mapModel, new PrimitiveModel<Integer>(index));
      }
    });
  }

  public ModelListPopup(@NotNull ListPopupStep aStep, ReactiveModel model, Path path) {
    super(aStep);
    myModel = model;
    myPath = path;
    myLifetime = Lifetime.Companion.create(Lifetime.Eternal);

  }

  @Override
  public void showUnderneathOfLabel(@NotNull JLabel label) {
  }

  @Override
  public void showInBestPositionFor(@NotNull Editor editor) {
    System.out.println("ModelListPopup.showInBestPositionFor");
    System.out.println("editor = [" + editor + "]");
    final EditorHost editorHost = editor.getUserData(EditorHost.editorHostKey);
    assert editorHost != null;
    final Path editorPath = editorHost.getPath();
    final MapModel context =
      new MapModel(ContainerUtil.newHashMap(new Pair<String, Model>("editor", ReactivemodelPackage.toList(editorPath))));
    show(context);
  }

  @Override
  public void dispose() {
    myLifetime.terminate();
    myModel.transaction(new Function1<MapModel, MapModel>() {
      @Override
      public MapModel invoke(MapModel mapModel) {
        return (MapModel)ReactivemodelPackage.putIn(myPath, mapModel, new AbsentModel());
      }
    });
    super.dispose();
  }

  private void show(final Model context) {
    final ListPopupStep<Object> listStep = getListStep();
    myModel.transaction(new Function1<MapModel, MapModel>() {
      @Override
      public MapModel invoke(MapModel mapModel) {
        final HashMap<String, MapModel> list = new HashMap<String, MapModel>();
        int i = 0;
        for (final Object value : listStep.getValues()) {
          list.put(String.valueOf(i++), new MapModel(new HashMap<String, Model>() {{
            put("text", new PrimitiveModel<String>(listStep.getTextFor(value)));
          }}));
        }
        mapModel = (MapModel)ReactivemodelPackage.putIn(myPath.div("list"), mapModel, new MapModel(list));
        final String title = listStep.getTitle();
        if (title != null) {
          mapModel = (MapModel)ReactivemodelPackage.putIn(myPath.div("title"), mapModel, new PrimitiveModel<String>(title));
        }
        if (context != null) {
          mapModel = (MapModel)ReactivemodelPackage.putIn(myPath.div("context"), mapModel, context);
        }
        mapModel = (MapModel)ReactivemodelPackage.putIn(myPath.div("selectedIndex"), mapModel, new PrimitiveModel<Integer>(mySelectedIndex));
        return mapModel;
      }
    });
    Signal<Model> selection = myModel.subscribe(myLifetime.getLifetime(), myPath.div("selectedIndex"));
    Signal<Model> actionPerformed = myModel.subscribe(myLifetime.getLifetime(), myPath.div("action"));
    final VariableSignal<Integer> reaction =
      ReactivemodelPackage.reaction(false, "selectedIndex reaction", selection, new Function1<Model, Integer>() {
        @Override
        public Integer invoke(Model model) {
          if (model instanceof PrimitiveModel) {
            mySelectedIndex = ((Integer)((PrimitiveModel)model).getValue());
          }
          return mySelectedIndex;
        }
      });
    ReactivemodelPackage.reaction(false, "perform action in list popup", actionPerformed, reaction, new Function2<Model, Integer, Object>() {
      @Override
      public Object invoke(Model action, Integer index) {
        if (action instanceof MapModel) {
          final PrimitiveModel<Boolean> finalChoice = (PrimitiveModel<Boolean>)((MapModel)action).get("finalChoice");
          handleSelect(finalChoice.getValue());
        }
        return null;
      }
    });
  }

  @Override
  public void show(Component owner, int aScreenX, int aScreenY, boolean considerForcedXY) {
    System.out.println("ModelListPopup.show");
    System.out.println("owner = [" +
                       owner +
                       "], aScreenX = [" +
                       aScreenX +
                       "], aScreenY = [" +
                       aScreenY +
                       "], considerForcedXY = [" +
                       considerForcedXY +
                       "]");
  }

  @Override
  public void showInCenterOf(@NotNull Component aContainer) {
    System.out.println("ModelListPopup.showInCenterOf");
    System.out.println("aContainer = [" + aContainer + "]");
  }

  @Override
  public void showCenteredInCurrentWindow(@NotNull Project project) {
    System.out.println("ModelListPopup.showCenteredInCurrentWindow");
    System.out.println("project = [" + project + "]");
  }

  @Override
  public void showUnderneathOf(@NotNull Component aComponent) {

  }

  @Override
  public void show(@NotNull RelativePoint aPoint) {

  }

  @Override
  public void showInScreenCoordinates(@NotNull Component owner, @NotNull Point point) {

  }

  @Override
  public void showInBestPositionFor(@NotNull DataContext dataContext) {

  }

  @Override
  public void showInFocusCenter() {

  }

  @Override
  public void show(Component owner) {

  }
}
