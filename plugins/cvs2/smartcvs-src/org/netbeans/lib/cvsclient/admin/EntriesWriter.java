package org.netbeans.lib.cvsclient.admin;

import java.io.File;
import java.io.IOException;

public interface EntriesWriter {
  void addEntry(final File directory, final Entry entry) throws IOException;
}
