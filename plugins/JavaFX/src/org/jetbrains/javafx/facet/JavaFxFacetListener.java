package org.jetbrains.javafx.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxFacetListener implements ModuleComponent {
  private MessageBusConnection myConnection;
  private final Module myModule;

  public JavaFxFacetListener(Module module) {
    myModule = module;
  }

  public void initComponent() {
    myConnection = myModule.getMessageBus().connect();
    myConnection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
      @Override
      public void beforeFacetRemoved(@NotNull Facet facet) {
        if (facet instanceof JavaFxFacet) {
          ((JavaFxFacet) facet).removeLibrary();
        }
      }

      @Override
      public void facetConfigurationChanged(@NotNull Facet facet) {
        if (facet instanceof JavaFxFacet) {
          ((JavaFxFacet) facet).updateLibrary();
        }
      }
    });
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void moduleAdded() {
  }

  @NotNull
  public String getComponentName() {
    return "JavaFxFacetListener";
  }

  public void disposeComponent() {
    myConnection.disconnect();
  }
}
