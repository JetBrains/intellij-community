/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.
 *
 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.event;

import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is responsible for firing CVS events to registered listeners.
 * It can either fire events as they are generated or wait until a suitable
 * checkpoint and fire many events at once. This can prevent event storms
 * from degrading system performance.
 * @author  Robert Greig
 */
public final class EventManager
        implements IEventSender, ICvsListenerRegistry {

        // Fields =================================================================

        private final List<ITerminationListener> terminationListeners = new ArrayList<>();
        private final List<IMessageListener> messageListener = new ArrayList<>();
        private final List<IModuleExpansionListener> moduleExpansionListeners = new ArrayList<>();
        private final List<IFileInfoListener> fileInfoListeners = new ArrayList<>();
        private final List<IEntryListener> entryListeners = new ArrayList<>();
        private final List<IDirectoryListener> directoryListeners = new ArrayList<>();
	    private final String myCharset;

	// Setup ==================================================================

        public EventManager(String charset) {
			myCharset = charset;
		}

        // Accessing ==============================================================

        @Override
        public synchronized void addTerminationListener(ITerminationListener listener) {
                terminationListeners.add(listener);
        }

        @Override
        public synchronized void removeTerminationListener(ITerminationListener listener) {
                terminationListeners.remove(listener);
        }

        @Override
        public synchronized void addMessageListener(IMessageListener listener) {
                messageListener.add(listener);
        }

        @Override
        public synchronized void removeMessageListener(IMessageListener listener) {
                messageListener.remove(listener);
        }

        @Override
        public synchronized void addModuleExpansionListener(IModuleExpansionListener listener) {
                moduleExpansionListeners.add(listener);
        }

        @Override
        public synchronized void removeModuleExpansionListener(IModuleExpansionListener listener) {
                moduleExpansionListeners.remove(listener);
        }

        @Override
        public void addEntryListener(IEntryListener listener) {
                entryListeners.add(listener);
        }

        @Override
        public void removeEntryListener(IEntryListener listener) {
                entryListeners.remove(listener);
        }

        @Override
        public synchronized void addFileInfoListener(IFileInfoListener listener) {
                fileInfoListeners.add(listener);
        }

        @Override
        public synchronized void removeFileInfoListener(IFileInfoListener listener) {
                fileInfoListeners.remove(listener);
        }

        @Override
        public synchronized void addDirectoryListener(IDirectoryListener listener) {
                directoryListeners.add(listener);
        }

        @Override
        public synchronized void removeDirectoryListener(IDirectoryListener listener) {
                directoryListeners.remove(listener);
        }

        // Actions ================================================================

        @Override
        public void notifyTerminationListeners(boolean error) {
                final ITerminationListener[] copiedListeners;
                synchronized (this) {
                  if (terminationListeners.isEmpty()) {
                                return;
                        }

                        copiedListeners = new ITerminationListener[terminationListeners.size()];
                        terminationListeners.toArray(copiedListeners);
                }

          for (ITerminationListener copiedListener : copiedListeners) {
            copiedListener.commandTerminated(error);
          }
        }

        @Override
        public void notifyMessageListeners(byte[] message, boolean error, boolean tagged) {
                final IMessageListener[] copiedListeners;
                synchronized (this) {
                  if (messageListener.isEmpty()) {
                                return;
                        }

                        copiedListeners = new IMessageListener[messageListener.size()];
                        messageListener.toArray(copiedListeners);
                }
          try {

                String stringMessage = new String(message, myCharset);
            for (IMessageListener copiedListener : copiedListeners) {

              copiedListener.messageSent(stringMessage, message, error, tagged);
            }
          }
          catch (UnsupportedEncodingException e) {
            //
          }

        }

        @Override
        public void notifyModuleExpansionListeners(String module) {
                final IModuleExpansionListener[] copiedListeners;
                synchronized (this) {
                  if (moduleExpansionListeners.isEmpty()) {
                                return;
                        }

                        copiedListeners = new IModuleExpansionListener[moduleExpansionListeners.size()];
                        moduleExpansionListeners.toArray(copiedListeners);
                }

          for (IModuleExpansionListener copiedListener : copiedListeners) {
            copiedListener.moduleExpanded(module);
          }
        }

        @Override
        public void notifyFileInfoListeners(Object fileInfoContainer) {
                final IFileInfoListener[] copiedListeners;
                synchronized (this) {
                  if (fileInfoListeners.isEmpty()) {
                                return;
                        }

                        copiedListeners = new IFileInfoListener[fileInfoListeners.size()];
                        fileInfoListeners.toArray(copiedListeners);
                }

          for (IFileInfoListener copiedListener : copiedListeners) {
            copiedListener.fileInfoGenerated(fileInfoContainer);
          }
        }

        @Override
        public void notifyFileInfoListeners(byte[] bytes) {
          final IMessageListener[] copiedListeners;
          synchronized (this) {
            if (messageListener.isEmpty()) {
                          return;
                  }

                  copiedListeners = new IMessageListener[messageListener.size()];
                  messageListener.toArray(copiedListeners);
          }

          for (IMessageListener copiedListener : copiedListeners) {
            copiedListener.binaryMessageSent(bytes);
          }


        }

  @Override
  public void notifyEntryListeners(FileObject fileObject, Entry entry) {
                final IEntryListener[] copiedListeners;
                synchronized (this) {
                  if (entryListeners.isEmpty()) {
                                return;
                        }

                        copiedListeners = new IEntryListener[entryListeners.size()];
                        entryListeners.toArray(copiedListeners);
                }

    for (IEntryListener copiedListener : copiedListeners) {
      copiedListener.gotEntry(fileObject, entry);
    }
        }

        @Override
        public void notifyDirectoryListeners(DirectoryObject directoryObject, boolean setStatic) {
          for (Object directoryListener1 : directoryListeners) {
            final IDirectoryListener directoryListener = (IDirectoryListener)directoryListener1;
            directoryListener.processingDirectory(directoryObject);
          }
        }
}