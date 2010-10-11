package org.jetbrains.javafx.sdk;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Key;
import org.jetbrains.javafx.facet.JavaFxFacet;

import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxSdkRootsListener implements RootProvider.RootSetChangedListener {
  public static final Key<JavaFxSdkRootsListener> JAVAFX_SDK_ROOTS_LISTENER_KEY = Key.create("javafx.sdk.root.listener");

  private static boolean rootEquals(final RootProvider rootProvider1, final RootProvider rootProvider2, final OrderRootType orderRootType) {
    return Arrays.equals(rootProvider1.getUrls(orderRootType), rootProvider2.getUrls(orderRootType));
  }

  private static void update(final Library.ModifiableModel libraryModifiableModel,
                             final RootProvider sdkRootProvider,
                             final OrderRootType orderRootType) {
    for (final String url : libraryModifiableModel.getUrls(orderRootType)) {
      libraryModifiableModel.removeRoot(url, orderRootType);
    }
    for (final String url : sdkRootProvider.getUrls(orderRootType)) {
      libraryModifiableModel.addRoot(url, orderRootType);
    }
  }

  private final Sdk mySdk;

  public JavaFxSdkRootsListener(final Sdk sdk) {
    mySdk = sdk;
  }

  public void rootSetChanged(final RootProvider wrapper) {
    final LibraryTable.ModifiableModel libraryTableModel =
      LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
    final Library library = libraryTableModel.getLibraryByName(JavaFxFacet.getFacetLibraryName(mySdk.getName()));
    if (library == null) {
      return;
    }
    final RootProvider libraryProvider = library.getRootProvider();
    final RootProvider sdkProvider = mySdk.getRootProvider();
    final Library.ModifiableModel libraryModifiableModel = library.getModifiableModel();

    if (!rootEquals(libraryProvider, sdkProvider, OrderRootType.CLASSES)) {
      update(libraryModifiableModel, sdkProvider, OrderRootType.CLASSES);
    }
    if (!rootEquals(libraryProvider, sdkProvider, OrderRootType.SOURCES)) {
      update(libraryModifiableModel, sdkProvider, OrderRootType.SOURCES);
    }

    libraryModifiableModel.commit();
  }
}