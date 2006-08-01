/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient.file;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Preprocesses the text file after getting from server.
 * @author  Thomas Singer
 */
public interface IReceiveTextFilePreprocessor {

	void copyTextFileToLocation(File textFileSource, File targetFile, IReaderFactory readerFactory, final Charset charSet) throws IOException;
}
