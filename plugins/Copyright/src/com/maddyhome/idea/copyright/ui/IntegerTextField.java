/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.maddyhome.idea.copyright.ui;

import javax.swing.*;
import java.text.Format;
import java.text.NumberFormat;
import java.text.ParseException;

public class IntegerTextField extends JFormattedTextField
{
    public IntegerTextField()
    {
    }

    public IntegerTextField(Object object)
    {
        super(object);
    }

    public IntegerTextField(Format format)
    {
        super(format);
    }

    public IntegerTextField(AbstractFormatter abstractFormatter)
    {
        super(abstractFormatter);
    }

    public IntegerTextField(AbstractFormatterFactory abstractFormatterFactory)
    {
        super(abstractFormatterFactory);
    }

    public IntegerTextField(AbstractFormatterFactory abstractFormatterFactory, Object object)
    {
        super(abstractFormatterFactory, object);
    }

    public void setRange(int min, int max)
    {
        setFormatterFactory(new IntegerFormatterFactory(min, max));
    }

    protected static class IntegerFormatterFactory extends JFormattedTextField.AbstractFormatterFactory
    {
        public IntegerFormatterFactory(int min, int max)
        {
            this.min = min;
            this.max = max;
        }

        public AbstractFormatter getFormatter(JFormattedTextField jFormattedTextField)
        {
            return new IntegerFormatter(min, max);
        }

        private final int min;
        private final int max;
    }

    protected static class IntegerFormatter extends JFormattedTextField.AbstractFormatter
    {
        public IntegerFormatter(int min, int max)
        {
            this.min = min;
            this.max = max;
        }

        public Object stringToValue(String string) throws ParseException
        {
            Number res = formatter.parse(string);
            if (res.intValue() < min || res.intValue() > max)
            {
                throw new ParseException("Out of range", 0);
            }

            return res;
        }

        public String valueToString(Object object) throws ParseException
        {
            if (object == null)
            {
                return null;
            }

            return object.toString();
        }

        private final int min;
        private final int max;
        private final NumberFormat formatter = NumberFormat.getIntegerInstance();
    }
}
