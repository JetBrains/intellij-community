package com.intellij.promoter;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "ShortcutPromoterManager",
  storages = {@Storage(file = StoragePathMacros.APP_CONFIG + "/promoter.xml", roamingType = RoamingType.PER_PLATFORM)}
)
public class ShortcutPromoterManager implements ApplicationComponent, AnActionListener, ExportableApplicationComponent,
                                                PersistentStateComponent<Element> {
  private final HashMap<String, PromoterState> myState = new LinkedHashMap<String, PromoterState>();
  private final HashMap<String, ShortcutPromoterEP> myExtensions = new HashMap<String, ShortcutPromoterEP>();

  @Override
  public void initComponent() {
    myExtensions.clear();
    myState.clear();

    for (ShortcutPromoterEP ep : ShortcutPromoterEP.EP_NAME.getExtensions()) {
      myExtensions.put(ep.actionId, ep);
    }
    ActionManager.getInstance().addAnActionListener(this);
  }

  @Override
  public void disposeComponent() {
    ActionManager.getInstance().removeAnActionListener(this);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return getClass().getName();
  }

  @Override
  public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
    final InputEvent input = event.getInputEvent();
    if (input instanceof MouseEvent) {
      final String id = ActionManager.getInstance().getId(action);
      final ShortcutPromoterEP ep = myExtensions.get(id);
      if (ep != null) {
        PromoterState state = myState.get(id);
        if (state == null) {
          state = new PromoterState();
          myState.put(id, state);
        }
        state.incClicks();
      }
    }
  }

  @Override
  public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {

  }

  @Override
  public void beforeEditorTyping(char c, DataContext dataContext) {

  }

  @NotNull
  @Override
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("shortcutPromoter")};
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Shortcut Promoter";
  }

  @Nullable
  @Override
  public Element getState() {
    final Element actions = new Element("actions");
    for (String id : myState.keySet()) {
      final Element action = new Element("action");
      action.setAttribute("actionId", id);
      action.setAttribute("clicks", String.valueOf(myState.get(id).getClicks()));
      actions.addContent(action);
    }
    return actions;
  }

  @Override
  public void loadState(Element state) {
    myState.clear();
    for (Element action : state.getChildren("action")) {
      final String id = action.getAttributeValue("actionId");
      final String clicks = action.getAttributeValue("clicks");
      try {
        final PromoterState info = new PromoterState();
        info.setClicks(Integer.parseInt(clicks));
        myState.put(id, info);
      } catch (Exception ignore) {}
    }
  }
}
