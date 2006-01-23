package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.versionBrowser.RepositoryVersion;
import org.netbeans.lib.cvsclient.command.log.Revision;

import java.util.*;

public class CvsChangeListsBuilder {
  private final Map<String, Map<String, List<RepositoryVersion>>> myCache = new HashMap<String, Map<String, List<RepositoryVersion>>>();

  private long myLastNumber = 0;
  private final String myRootPath;
  private final CvsEnvironment myEnvironment;
  private final Project myProject;

  public CvsChangeListsBuilder(final String rootPath, final CvsEnvironment environment, final Project project) {
    myRootPath = rootPath;
    myEnvironment = environment;
    myProject = project;
  }

  public List<RepositoryVersion> getVersions() {
    final ArrayList<RepositoryVersion> result = new ArrayList<RepositoryVersion>();
    for (Map<String, List<RepositoryVersion>> byMessage : myCache.values()) {
      for (List<RepositoryVersion> versions : byMessage.values()) {
        result.addAll(versions);
      }
    }
    return result;
  }

  private void addRevision(RevisionWrapper revision) {
    final Revision cvsRevision = revision.getRevision();
    CvsRepositoryVersion version = findOrCreateVersionFor(cvsRevision.getMessage(),
                                                          revision.getTime(),
                                                          cvsRevision.getAuthor());

    version.addFileRevision(revision);
  }

  private CvsRepositoryVersion findOrCreateVersionFor(final String message, final long date, final String author) {
    final Map<String, List<RepositoryVersion>> byMessage = myCache.get(message);
    if (byMessage != null) {
      final List<RepositoryVersion> versions = byMessage.get(author);
      if (versions != null) {
        final CvsRepositoryVersion lastVersion = (CvsRepositoryVersion)versions.get(versions.size() - 1);
        if (lastVersion.containsDate(date)) {
          return lastVersion;
        }
      }
    }

    final CvsRepositoryVersion result = new CvsRepositoryVersion(myLastNumber, message, date, author, myRootPath, myEnvironment,
                                                                 myProject);
    myLastNumber += 1;

    if (!myCache.containsKey(message)) {
      myCache.put(message, new HashMap<String, List<RepositoryVersion>>());
    }

    final Map<String, List<RepositoryVersion>> filteredByMessages = myCache.get(message);
    if (!filteredByMessages.containsKey(author)) {
      filteredByMessages.put(author, new ArrayList<RepositoryVersion>());
    }

    filteredByMessages.get(author).add(result);

    return result;
  }

  public void addLogs(final List<LogInformationWrapper> logs) {
    List<RevisionWrapper> revisionWrappers = new ArrayList<RevisionWrapper>();

    for (LogInformationWrapper log : logs) {
      final String file = log.getFile();
      if (CvsFileRevision.isAncestor(myRootPath, file)) {
        for (Revision revision : log.getRevisions()) {
          revisionWrappers.add(new RevisionWrapper(file, revision));
        }
      }
    }

    Collections.sort(revisionWrappers);


    for (RevisionWrapper revisionWrapper : revisionWrappers) {
      addRevision(revisionWrapper);
    }
  }
}
