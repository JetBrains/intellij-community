/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;

import javax.swing.text.DefaultFormatter;
import java.text.ParseException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegExFormatter extends DefaultFormatter {

    public RegExFormatter() {
        super();
        setOverwriteMode(false);
    }

    public Object stringToValue(String string) throws ParseException {
        try {
            return Pattern.compile(string);
        } catch (final PatternSyntaxException e) {
            throw new ParseException(e.getMessage(), e.getIndex());
        }
    }

    public String valueToString(Object value) throws ParseException {
        if (value == null) {
            return "";
        }
        return ((Pattern) value).pattern();
    }
}