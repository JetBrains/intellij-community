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
 * Marks the end of a stream that might have contained multiple documents.
 * <p>
 * This event is the last event that a parser emits. Together with
 * {@link StreamStartEvent} (which is the first event a parser emits) they mark
 * the beginning and the end of a stream of documents.
 * </p>
 * <p>
 * See {@link Event} for an exemplary output.
 * </p>
 */
public final class StreamEndEvent extends Event {
    public StreamEndEvent(Optional<Mark> startMark, Optional<Mark> endMark) {
        super(startMark, endMark);
    }

    public StreamEndEvent() {
        super();
    }

    @Override
    public ID getEventId() {
        return ID.StreamEnd;
    }

    @Override
    public String toString() {
        return "-STR";
    }
}
