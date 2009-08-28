package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CachesHolder {
  @NonNls private static final String VCS_CACHE_PATH = "vcsCache";
  
  private final Project myProject;
  private final Map<String, ChangesCacheFile> myCacheFiles;
  private final RepositoryLocationCache myLocationCache;
  private final ProjectLevelVcsManager myPlManager;

  public CachesHolder(final Project project, final RepositoryLocationCache locationCache) {
    myProject = project;
    myLocationCache = locationCache;
    myPlManager = ProjectLevelVcsManager.getInstance(myProject);
    myCacheFiles = new ConcurrentHashMap<String, ChangesCacheFile>();
  }

  /**
   * Returns all paths that will be used to collect committed changes about. ideally, for one checkout there should be one file
   */
  public List<VirtualFile> getAllRootsUnderVcs(final AbstractVcs vcs) {
    final RootsCalculator calculator = new RootsCalculator(myProject, vcs);
    return calculator.getRoots();
  }

  public void iterateAllCaches(final NotNullFunction<ChangesCacheFile, Boolean> consumer) {
    final AbstractVcs[] vcses = myPlManager.getAllActiveVcss();
    for (AbstractVcs vcs : vcses) {
      final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
      if (provider instanceof CachingCommittedChangesProvider) {
        final List<VirtualFile> roots = getAllRootsUnderVcs(vcs);
        for (VirtualFile root : roots) {
          final RepositoryLocation location = myLocationCache.getLocation(vcs, new FilePathImpl(root));
          if (location != null) {
            final ChangesCacheFile cacheFile = getCacheFile(vcs, root, location);
            if (Boolean.TRUE.equals(consumer.fun(cacheFile))) {
              return;
            }
          }
        }
      }
    }
  }

  public List<ChangesCacheFile> getAllCaches() {
    final List<ChangesCacheFile> result = new ArrayList<ChangesCacheFile>();
    iterateAllCaches(new NotNullFunction<ChangesCacheFile, Boolean>() {
      @NotNull
      public Boolean fun(final ChangesCacheFile changesCacheFile) {
        result.add(changesCacheFile);
        return false;
      }
    });
    return result;
  }

  public ChangesCacheFile getCacheFile(AbstractVcs vcs, VirtualFile root, RepositoryLocation location) {
    final String key = location.getKey();
    ChangesCacheFile cacheFile = myCacheFiles.get(key);
    if (cacheFile == null) {
      cacheFile = new ChangesCacheFile(myProject, getCachePath(location), vcs, root, location);
      myCacheFiles.put(key, cacheFile);
    }
    return cacheFile;
  }

  public File getCacheBasePath() {
    File file = new File(PathManager.getSystemPath(), VCS_CACHE_PATH);
    file = new File(file, myProject.getLocationHash());
    return file;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private File getCachePath(final RepositoryLocation location) {
    File file = getCacheBasePath();
    file.mkdirs();
    String s = location.getKey();
    try {
      final byte[] bytes = MessageDigest.getInstance("MD5").digest(CharsetToolkit.getUtf8Bytes(s));
      StringBuilder result = new StringBuilder();
      for (byte aByte : bytes) {
        result.append(String.format("%02x", aByte));
      }
      return new File(file, result.toString());
    }
    catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
