package com.intellij.workspace.legacyBridge.roots

/*
class LegacyBridgeProjectFileIndex(val fileTypeRegistry: FileTypeRegistry,
                                   val entityStore: EntityStore) : ProjectFileIndex {
  private val trieCachedValue = CachedValue {
    EntityTrie(it) { aspect ->
      return@EntityTrie when (aspect) {
        is ContentEntryAspect -> VirtualFileUrlManager.fromUrl(aspect.url)
        is SourceFolderAspect -> VirtualFileUrlManager.fromUrl(aspect.url)
        is ExcludeFolderAspect -> VirtualFileUrlManager.fromUrl(aspect.url)
        // TODO ExcludePatternAspect ?
        else -> null
      }
    }
  }

  override fun isUnderIgnored(file: VirtualFile): Boolean {
    val trie = entityStore.cachedValue(trieCachedValue)

    val nodes = trie.lookup(VirtualFileUrlManager.fromVirtualFile(file))

    for (list in nodes) {
      if (list.any { it.tryGetAspect<SourceFolderAspect>() != null } ||
          list.any { it.tryGetAspect<ContentEntryAspect>() != null }) return false

      if (list.any { it.tryGetAspect<ExcludeFolderAspect>() != null }) return true
    }

    return false
  }

  override fun iterateContent(processor: ContentIterator): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun iterateContent(processor: ContentIterator, filter: VirtualFileFilter?): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getModuleForFile(file: VirtualFile): Module? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getModuleForFile(file: VirtualFile, honorExclusion: Boolean): Module? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isIgnored(file: VirtualFile): Boolean = isExcluded(file)

  override fun isInSourceContent(fileOrDir: VirtualFile): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getOrderEntriesForFile(file: VirtualFile): MutableList<OrderEntry> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getClassRootForFile(file: VirtualFile): VirtualFile? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getSourceRootForFile(file: VirtualFile): VirtualFile? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getPackageNameByDirectory(dir: VirtualFile): String? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isInContent(fileOrDir: VirtualFile): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isInTestSourceContent(fileOrDir: VirtualFile): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getContentRootForFile(file: VirtualFile): VirtualFile? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getContentRootForFile(file: VirtualFile, honorExclusion: Boolean): VirtualFile? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isLibraryClassFile(file: VirtualFile): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isInSource(fileOrDir: VirtualFile): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isInLibraryClasses(fileOrDir: VirtualFile): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isInLibrary(fileOrDir: VirtualFile): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isExcluded(file: VirtualFile): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun iterateContentUnderDirectory(dir: VirtualFile, processor: ContentIterator): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun iterateContentUnderDirectory(dir: VirtualFile, processor: ContentIterator, customFilter: VirtualFileFilter?): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isContentSourceFile(file: VirtualFile): Boolean {
    return !file.isDirectory &&
           !fileTypeRegistry.isFileIgnored(file) &&
           isInSourceContent(file)
  }

  override fun isUnderSourceRootOfType(fileOrDir: VirtualFile, rootTypes: MutableSet<out JpsModuleSourceRootType<*>>): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isInLibrarySource(fileOrDir: VirtualFile): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}*/
