package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.openapi.project.Project;
import org.netbeans.lib.cvsclient.command.log.Revision;

import java.util.*;

public class CvsChangeListsBuilder {
  private final Map<String, Map<String, List<CvsChangeList>>> myCache = new HashMap<String, Map<String, List<CvsChangeList>>>();

  private long myLastNumber = 0;
  private final String myRootPath;
  private final CvsEnvironment myEnvironment;
  private final Project myProject;

  public CvsChangeListsBuilder(final String rootPath, final CvsEnvironment environment, final Project project) {
    myRootPath = rootPath;
    myEnvironment = environment;
    myProject = project;
  }

  public List<CvsChangeList> getVersions() {
    final ArrayList<CvsChangeList> result = new ArrayList<CvsChangeList>();
    for (Map<String, List<CvsChangeList>> byMessage : myCache.values()) {
      for (List<CvsChangeList> versions : byMessage.values()) {
        result.addAll(versions);
      }
    }
    return result;
  }

  private void addRevision(RevisionWrapper revision) {
    final Revision cvsRevision = revision.getRevision();
    CvsChangeList version = findOrCreateVersionFor(cvsRevision.getMessage(),
                                                   revision.getTime(),
                                                   cvsRevision.getAuthor());

    version.addFileRevision(revision);
  }

  private CvsChangeList findOrCreateVersionFor(final String message, final long date, final String author) {
    final Map<String, List<CvsChangeList>> byMessage = myCache.get(message);
    if (byMessage != null) {
      final List<CvsChangeList> versions = byMessage.get(author);
      if (versions != null) {
        final CvsChangeList lastVersion = versions.get(versions.size() - 1);
        if (lastVersion.containsDate(date)) {
          return lastVersion;
        }
      }
    }

    final CvsChangeList result = new CvsChangeList(myLastNumber, message, date, author, myRootPath, myEnvironment, myProject);
    myLastNumber += 1;

    if (!myCache.containsKey(message)) {
      myCache.put(message, new HashMap<String, List<CvsChangeList>>());
    }

    final Map<String, List<CvsChangeList>> filteredByMessages = myCache.get(message);
    if (!filteredByMessages.containsKey(author)) {
      filteredByMessages.put(author, new ArrayList<CvsChangeList>());
    }

    filteredByMessages.get(author).add(result);

    return result;
  }

  public void addLogs(final List<LogInformationWrapper> logs) {
    List<RevisionWrapper> revisionWrappers = new ArrayList<RevisionWrapper>();

    for (LogInformationWrapper log : logs) {
      final String file = log.getFile();
      if (CvsChangeList.isAncestor(myRootPath, file)) {
        for (Revision revision : log.getRevisions()) {
          if (revision != null) {
            revisionWrappers.add(new RevisionWrapper(file, revision));
          }
        }
      }
    }

    Collections.sort(revisionWrappers);


    for (RevisionWrapper revisionWrapper : revisionWrappers) {
      addRevision(revisionWrapper);
    }
  }
}
