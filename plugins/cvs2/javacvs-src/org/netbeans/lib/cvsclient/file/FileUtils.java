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

import org.netbeans.lib.cvsclient.JavaCvsSrcBundle;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.StringTokenizer;

/**
 * A utility class for file based operations.
 *
 * @author  Thomas Singer
 */
public final class FileUtils {
  @NonNls private static final String U_PREFIX = "u=";

  /**
   * Copies the specified sourceFile to the specified targetFile.
   */
  public static void copyFile(File sourceFile, File targetFile) throws IOException {
          if (sourceFile == null || targetFile == null) {
                  throw new NullPointerException("sourceFile and targetFile must not be null"); // NOI18N
          }

          // ensure existing parent directories
          final File directory = targetFile.getParentFile();
          if (!directory.exists() && !directory.mkdirs()) {
                  throw new IOException(JavaCvsSrcBundle.message("could.not.create.directory.error.message", directory)); // NOI18N
          }

          InputStream inputStream = null;
          OutputStream outputStream = null;
          try {
                  inputStream = new BufferedInputStream(new FileInputStream(sourceFile));
                  outputStream = new BufferedOutputStream(new FileOutputStream(targetFile));

                  final byte[] buffer = new byte[32768];
                  for (int readBytes = inputStream.read(buffer);
                       readBytes > 0;
                       readBytes = inputStream.read(buffer)) {
                          outputStream.write(buffer, 0, readBytes);
                  }
          }
          finally {
                  if (inputStream != null) {
                          try {
                                  inputStream.close();
                          }
                          catch (IOException ex) {
                                  // ignore
                          }
                  }
                  if (outputStream != null) {
                          try {
                                  outputStream.close();
                          }
                          catch (IOException ex) {
                                  // ignore
                          }
                  }
          }
  }

	public static String ensureLeadingSlash(String filePath) {
		if (filePath.startsWith("/")) {
			return filePath;
		}
		return '/' + filePath;
	}

	public static String removeLeadingSlash(String filePath) {
		if (filePath.startsWith("/")) {
			return filePath.substring(1);
		}
		return filePath;
	}

	public static String ensureTrailingSlash(String pathName) {
		if (pathName.endsWith("/")) {
			return pathName;
		}
		return pathName + '/';
	}

	public static String removeTrailingSlash(String pathName) {
		if (pathName.endsWith("/")) {
			return pathName.substring(0, pathName.length() - 1);
		}
		return pathName;
	}

	public static boolean isReadOnlyMode(String mode) {
		for (StringTokenizer tokenizer = new StringTokenizer(mode, ","); tokenizer.hasMoreTokens();) {
			final String token = tokenizer.nextToken();
			if (token.startsWith(U_PREFIX)) {
				return token.indexOf('w') < 0;
			}
		}
		return false;
	}

	public static String readLineFromFile(File file) throws IOException {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			return reader.readLine();
		}
		finally {
			if (reader != null) {
				reader.close();
			}
		}
	}

	public static void writeLine(File file, String line) throws IOException {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(file));
			writer.write(line);
			writer.newLine();
		}
		finally {
			if (writer != null) {
				try {
					writer.close();
				}
				catch (IOException ex) {
					// ignore
				}
			}
		}
	}

	/**
	 * This utility class needs not to be instantiated anywhere.
	 */
	private FileUtils() {
	}
}
