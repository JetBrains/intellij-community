package org.jetbrains.javafx.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.sdk.JavaFxSdkListener;
import org.jetbrains.javafx.sdk.JavaFxSdkUtil;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxFacet extends Facet<JavaFxFacetConfiguration> {
  public static final FacetTypeId<JavaFxFacet> ID = new FacetTypeId<JavaFxFacet>("javafx");
  public static final FacetType<JavaFxFacet, JavaFxFacetConfiguration> FACET_TYPE = new JavaFxFacetType();
  private static final String JAVAFX_FACET_LIBRARY_PREFIX = "Library for: ";

  public JavaFxFacet(@NotNull final Module module,
                     @NotNull final String name,
                     @NotNull final JavaFxFacetConfiguration configuration) {
    super(FACET_TYPE, module, name, configuration, null);
  }

  public static String getFacetLibraryName(final String sdkName) {
    return JAVAFX_FACET_LIBRARY_PREFIX + sdkName;
  }

  void updateLibrary() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final Module module = getModule();
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        boolean modelChanged = false;
        // Just remove all old facet libraries except one, that is neccessary
        final Sdk sdk = JavaFxSdkUtil.getSdk(JavaFxFacet.this);
        final String name = (sdk != null) ? getFacetLibraryName(sdk.getName()) : null;
        boolean librarySeen = false;
        for (OrderEntry entry : model.getOrderEntries()) {
          if (entry instanceof LibraryOrderEntry) {
            final String libraryName = ((LibraryOrderEntry)entry).getLibraryName();
            if (name != null && name.equals(libraryName)) {
              librarySeen = true;
              continue;
            }
            if (libraryName != null && StringUtil.startsWith(libraryName, JAVAFX_FACET_LIBRARY_PREFIX)) {
              model.removeOrderEntry(entry);
              modelChanged = true;
            }
          }
        }
        if (!librarySeen && name != null) {
          Library library = LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraryByName(name);
          if (library == null) {
            // we just create new project library
            library = JavaFxSdkListener.addLibrary(sdk);
          }
          model.addLibraryEntry(library);
          modelChanged = true;
        }
        if (modelChanged) {
          model.commit();
        }
        else {
          model.dispose();
        }
      }
    });
  }

  void removeLibrary() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run()  {
        final Module module = getModule();
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        // Just remove all old facet libraries
        for (OrderEntry entry : model.getOrderEntries()) {
          if (entry instanceof LibraryOrderEntry) {
            final Library library = ((LibraryOrderEntry)entry).getLibrary();
            if (library != null) {
              final String libraryName = library.getName();
              if (libraryName != null && libraryName.startsWith(JAVAFX_FACET_LIBRARY_PREFIX)) {
                model.removeOrderEntry(entry);
              }
            }
          }
        }
        model.commit();
      }
    });
  }

  @Override
  public void initFacet() {
    super.initFacet();
    updateLibrary();
  }
}
