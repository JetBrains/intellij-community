/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.generate.tostring.util;

import com.intellij.openapi.util.text.StringUtil;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility methods for file IO.
 */
public class FileUtil {

    /**
     * Private constructor, as only static methods allowed.
     */
    private FileUtil() {
    }

    /**
     * Reads the content of the resource and return it as a String.
     * <p/>Uses the classloader that loaded this class to find the resource in its classpath.
     *
     * @param resource the resouce name. Will lookup using the classpath.
     * @return the content if the resource
     * @throws IOException   error reading the file.
     */
    public static String readFile(String resource) throws IOException {
        BufferedInputStream in = new BufferedInputStream(FileUtil.class.getResourceAsStream(resource));
        return readFileContent(in);
    }

  /**
     * Reads the files content and return it as a String.
     *
     * @param in   the file input stream.
     * @return   the file content.
     * @throws IOException   error reading the file.
     */
    public static String readFileContent(InputStream in) throws IOException {
        StringBuffer buf = new StringBuffer();
        for (int i = in.read(); i != -1; i = in.read()) {
            buf.append((char) i);
        }
        return StringUtil.convertLineSeparators(buf.toString());
    }
}
