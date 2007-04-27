package com.intellij.cvsSupport2.changeBrowser;

import org.netbeans.lib.cvsclient.command.log.Revision;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LogInformationWrapper {
  private final String myFile;
  private final List<Revision> myRevisions;
  @NonNls static final String CVS_REPOSITORY_FILE_POSTFIX = ",v";

  public LogInformationWrapper(final String file, final List<Revision> revisions) {
    myFile = file;
    myRevisions = revisions;
  }

  public String getFile() {
    return myFile;
  }

  public List<Revision> getRevisions() {
    return myRevisions;
  }

  @Nullable
  public static LogInformationWrapper wrap(final String repository, final LogInformation log) {
    LogInformationWrapper wrapper = null;
    if (!log.getRevisionList().isEmpty()) {

      final String rcsFileName = log.getRcsFileName();
      if (rcsFileName.startsWith(repository)) {
        String relativePath = rcsFileName.substring(repository.length());
        if (relativePath.startsWith("/")) {
          relativePath = relativePath.substring(1);
        }

        if (relativePath.endsWith(CVS_REPOSITORY_FILE_POSTFIX)) {
          relativePath = relativePath.substring(0, relativePath.length() - CVS_REPOSITORY_FILE_POSTFIX.length());
        }

        //noinspection unchecked
        wrapper = new LogInformationWrapper(relativePath, log.getRevisionList());
      }
    }
    return wrapper;
  }
}
