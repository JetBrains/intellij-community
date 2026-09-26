// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/// Defines a cleaner that knows how to delete the storage's data.
/// It could be seen as a counterpart to a [StorageFactory], that knows how to create/open a storage -- both are
/// typically implemented together, by a same class.
///
/// **BEWARE**: cleaning storage data is not very well-defined task, especially so because it is often needed after
/// some faulty scenario -- i.e. when storage is failed to fully initialize, or gets corrupted. In those cases it
/// could be unclear which data files belong to a storage, and which files are just random bystanders:
/// - what if the storage file has been created, but its header hasn't been initialized -- so we can't be sure the
///   file really belongs to the storage
/// - only some storage files could exist, while other storage files are missed -- again, making it unclear are
///   those files belong to a partially-initialized storage, or they are random bystanders
///
/// Suggested rule for implementation: **drop everything that looks like a storage's data-file**.
///
/// Why: clearing methods are often used to ensure 'blank state' after some nasty crashes/corruptions.
/// If an implementation avoids cleaning some mess because 'can't be sure this is not a foreign file' -- it makes
/// the app unable to recover from the crash.
///
/// So, do not hesitate: the Lord knows those that are His -- it is up to using code to put the storages in dedicated
/// folders, so no random bystanders are wondering around.
///
/// @see StorageFactory
@ApiStatus.Internal
public interface StorageCleaner<A extends AutoCloseable> {
  /// Closes the storage, and cleans all the data that belongs to it;
  /// It is up to implementation how to deal with already closed storages: some implementation may be able to clean
  /// after it, other may opt to throw exception
  void closeAndClean(@NotNull A storage) throws IOException;

  /// Cleans all the data that _may_ belong to the storage implementation known by this cleaner.
  ///
  /// _Approximately_ it should be equivalent to `closeAndClean(storageFactory.open(storagePath))`, but:
  /// a. it should be no opened storage instance for storagePath at the moment of [#cleanData] call: [#closeAndClean]
  ///    closes the storage by itself, but here it is up to calling code to ensure that;
  /// b. implementation is _not_ required to check the data files are really belong to the storage -- some foreign files
  ///    with name matching the storage naming convention may be removed;
  void cleanData(@NotNull Path storagePath) throws IOException;
}
