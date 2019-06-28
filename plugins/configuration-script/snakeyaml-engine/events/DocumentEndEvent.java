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
package org.snakeyaml.engine.v1.events;

import java.util.Optional;

import org.snakeyaml.engine.v1.exceptions.Mark;

/**
 * Marks the end of a document.
 * <p>
 * This event follows the document's content.
 * </p>
 */
public final class DocumentEndEvent extends Event {
    private final boolean explicit;

    public DocumentEndEvent(boolean explicit, Optional<Mark> startMark, Optional<Mark> endMark) {
        super(startMark, endMark);
        this.explicit = explicit;
    }

    public DocumentEndEvent(boolean explicit) {
        this(explicit, Optional.empty(), Optional.empty());
    }

    public boolean isExplicit() {
        return explicit;
    }

    @Override
    public ID getEventId() {
        return ID.DocumentEnd;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("-DOC");
        if (isExplicit()) {
            builder.append(" ...");
        }
        return builder.toString();
    }
}
