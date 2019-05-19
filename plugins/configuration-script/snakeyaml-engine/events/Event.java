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
 * Basic unit of output from a {@link org.snakeyaml.engine.v1.parser.Parser} or input
 * of a {@link org.snakeyaml.engine.v1.emitter.Emitter}.
 */
public abstract class Event {
    public enum ID {
        Alias, DocumentEnd, DocumentStart, MappingEnd, MappingStart, Scalar, SequenceEnd, SequenceStart, StreamEnd, StreamStart
    }

    private final Optional<Mark> startMark;
    private final Optional<Mark> endMark;

    public Event(Optional<Mark> startMark, Optional<Mark> endMark) {
        if ((startMark.isPresent() && !endMark.isPresent()) || (!startMark.isPresent() && endMark.isPresent())) {
            throw new NullPointerException("Both marks must be either present or absent.");
        }
        this.startMark = startMark;
        this.endMark = endMark;
    }

    /*
     * Create Node for emitter
     */
    public Event() {
        this(Optional.empty(), Optional.empty());
    }

    public Optional<Mark> getStartMark() {
        return startMark;
    }

    public Optional<Mark> getEndMark() {
        return endMark;
    }

    public final boolean isEvent(Event.ID id) {
        return getEventId() == id;
    }

    public abstract Event.ID getEventId();

    /*
     * for tests only
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Event) {
            return toString().equals(obj.toString());
        } else {
            return false;
        }
    }

    /*
     * for tests only
     */
    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
