/**
 * Copyright (c) 2018, http://www.snakeyaml.org
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.snakeyaml.engine.v1.scanner;

import java.util.Optional;

import org.snakeyaml.engine.v1.exceptions.Mark;

/**
 * Simple keys treatment.
 * <p>
 * Helper class for {@link ScannerImpl}.
 * </p>
 *
 * @see ScannerImpl
 */
final class SimpleKey {
    private int tokenNumber;
    private boolean required;
    private int index;
    private int line;
    private int column;
    private Optional<Mark> mark;

    public SimpleKey(int tokenNumber, boolean required, int index, int line, int column, Optional<Mark> mark) {
        this.tokenNumber = tokenNumber;
        this.required = required;
        this.index = index;
        this.line = line;
        this.column = column;
        this.mark = mark;
    }

    public int getTokenNumber() {
        return this.tokenNumber;
    }

    public int getColumn() {
        return this.column;
    }

    public Optional<Mark> getMark() {
        return mark;
    }

    public int getIndex() {
        return index;
    }

    public int getLine() {
        return line;
    }

    public boolean isRequired() {
        return required;
    }

    @Override
    public String toString() {
        return "SimpleKey - tokenNumber=" + tokenNumber + " required=" + required + " index="
                + index + " line=" + line + " column=" + column;
    }
}
