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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.snakeyaml.engine.v1.common.SpecVersion;
import org.snakeyaml.engine.v1.exceptions.Mark;

/**
 * Marks the beginning of a document.
 * <p>
 * This event followed by the document's content and a {@link DocumentEndEvent}.
 * </p>
 */
public final class DocumentStartEvent extends Event {
    private final boolean explicit;
    private final Optional<SpecVersion> specVersion;
    private final Map<String, String> tags;

    public DocumentStartEvent(boolean explicit, Optional<SpecVersion> specVersion,
                              Map<String, String> tags, Optional<Mark> startMark, Optional<Mark> endMark) {
        super(startMark, endMark);
        this.explicit = explicit;
        Objects.requireNonNull(specVersion);
        this.specVersion = specVersion;
        Objects.requireNonNull(tags);
        this.tags = tags;
    }

    public DocumentStartEvent(boolean explicit, Optional<SpecVersion> specVersion, Map<String, String> tags) {
        this(explicit, specVersion, tags, Optional.empty(), Optional.empty());
    }

    public boolean isExplicit() {
        return explicit;
    }

    /**
     * @return YAML version the document conforms to.
     */
    public Optional<SpecVersion> getSpecVersion() {
        return specVersion;
    }

    /**
     * Tag shorthands as defined by the <code>%TAG</code> directive.
     *
     * @return Mapping of 'handles' to 'prefixes' (the handles include the '!'
     * characters).
     */
    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public ID getEventId() {
        return ID.DocumentStart;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("+DOC");
        if (isExplicit()) {
            builder.append(" ---");
        }
        return builder.toString();
    }
}
