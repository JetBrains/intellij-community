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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.io.UnsupportedEncodingException;

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

        private final List terminationListeners = new ArrayList();
        private final List messageListener = new ArrayList();
        private final List moduleExpansionListeners = new ArrayList();
        private final List fileInfoListeners = new ArrayList();
        private final List entryListeners = new ArrayList();
        private final List directoryListeners = new ArrayList();
	    private final String myCharset;

	// Setup ==================================================================

        public EventManager(String charset) {
			myCharset = charset;
		}

        // Accessing ==============================================================

        public synchronized void addTerminationListener(ITerminationListener listener) {
                terminationListeners.add(listener);
        }

        public synchronized void removeTerminationListener(ITerminationListener listener) {
                terminationListeners.remove(listener);
        }

        public synchronized void addMessageListener(IMessageListener listener) {
                messageListener.add(listener);
        }

        public synchronized void removeMessageListener(IMessageListener listener) {
                messageListener.remove(listener);
        }

        public synchronized void addModuleExpansionListener(IModuleExpansionListener listener) {
                moduleExpansionListeners.add(listener);
        }

        public synchronized void removeModuleExpansionListener(IModuleExpansionListener listener) {
                moduleExpansionListeners.remove(listener);
        }

        public void addEntryListener(IEntryListener listener) {
                entryListeners.add(listener);
        }

        public void removeEntryListener(IEntryListener listener) {
                entryListeners.remove(listener);
        }

        public synchronized void addFileInfoListener(IFileInfoListener listener) {
                fileInfoListeners.add(listener);
        }

        public synchronized void removeFileInfoListener(IFileInfoListener listener) {
                fileInfoListeners.remove(listener);
        }

        public synchronized void addDirectoryListener(IDirectoryListener listener) {
                directoryListeners.add(listener);
        }

        public synchronized void removeDirectoryListener(IDirectoryListener listener) {
                directoryListeners.remove(listener);
        }

        // Actions ================================================================

        public void notifyTerminationListeners(boolean error) {
                final ITerminationListener[] copiedListeners;
                synchronized (this) {
                        if (terminationListeners.size() == 0) {
                                return;
                        }

                        copiedListeners = new ITerminationListener[terminationListeners.size()];
                        terminationListeners.toArray(copiedListeners);
                }

                for (int i = 0; i < copiedListeners.length; i++) {
                        copiedListeners[i].commandTerminated(error);
                }
        }

        public void notifyMessageListeners(byte[] message, boolean error, boolean tagged) {
                final IMessageListener[] copiedListeners;
                synchronized (this) {
                        if (messageListener.size() == 0) {
                                return;
                        }

                        copiedListeners = new IMessageListener[messageListener.size()];
                        messageListener.toArray(copiedListeners);
                }
          try {

                String stringMessage = new String(message, myCharset);
                for (int i = 0; i < copiedListeners.length; i++) {

                    copiedListeners[i].messageSent(stringMessage, message, error, tagged);
                }
          }
          catch (UnsupportedEncodingException e) {
            //
          }

        }

        public void notifyModuleExpansionListeners(String module) {
                final IModuleExpansionListener[] copiedListeners;
                synchronized (this) {
                        if (moduleExpansionListeners.size() == 0) {
                                return;
                        }

                        copiedListeners = new IModuleExpansionListener[moduleExpansionListeners.size()];
                        moduleExpansionListeners.toArray(copiedListeners);
                }

                for (int i = 0; i < copiedListeners.length; i++) {
                        copiedListeners[i].moduleExpanded(module);
                }
        }

        public void notifyFileInfoListeners(Object fileInfoContainer) {
                final IFileInfoListener[] copiedListeners;
                synchronized (this) {
                        if (fileInfoListeners.size() == 0) {
                                return;
                        }

                        copiedListeners = new IFileInfoListener[fileInfoListeners.size()];
                        fileInfoListeners.toArray(copiedListeners);
                }

                for (int i = 0; i < copiedListeners.length; i++) {
                        copiedListeners[i].fileInfoGenerated(fileInfoContainer);
                }
        }

        public void notifyFileInfoListeners(byte[] bytes) {
          final IMessageListener[] copiedListeners;
          synchronized (this) {
                  if (messageListener.size() == 0) {
                          return;
                  }

                  copiedListeners = new IMessageListener[messageListener.size()];
                  messageListener.toArray(copiedListeners);
          }

          for (int i = 0; i < copiedListeners.length; i++) {
                  copiedListeners[i].binaryMessageSent(bytes);
          }


        }

  public void notifyEntryListeners(FileObject fileObject, Entry entry) {
                final IEntryListener[] copiedListeners;
                synchronized (this) {
                        if (entryListeners.size() == 0) {
                                return;
                        }

                        copiedListeners = new IEntryListener[entryListeners.size()];
                        entryListeners.toArray(copiedListeners);
                }

                for (int i = 0; i < copiedListeners.length; i++) {
                        copiedListeners[i].gotEntry(fileObject, entry);
                }
        }

        public void notifyDirectoryListeners(DirectoryObject directoryObject, boolean setStatic) {
                for (Iterator it = directoryListeners.iterator(); it.hasNext();) {
                        final IDirectoryListener directoryListener = (IDirectoryListener)it.next();
                        directoryListener.processingDirectory(directoryObject);
                }
        }
}