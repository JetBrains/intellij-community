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

import org.snakeyaml.engine.v1.common.Anchor;
import org.snakeyaml.engine.v1.common.FlowStyle;
import org.snakeyaml.engine.v1.exceptions.Mark;

/**
 * Marks the beginning of a sequence node.
 * <p>
 * This event is followed by the elements contained in the sequence, and a
 * {@link SequenceEndEvent}.
 * </p>
 *
 * @see SequenceEndEvent
 */
public final class SequenceStartEvent extends CollectionStartEvent {
    public SequenceStartEvent(Optional<Anchor> anchor, Optional<String> tag, boolean implicit, FlowStyle flowStyle, Optional<Mark> startMark,
                              Optional<Mark> endMark) {
        super(anchor, tag, implicit, flowStyle, startMark, endMark);
    }

    public SequenceStartEvent(Optional<Anchor> anchor, Optional<String> tag, boolean implicit, FlowStyle flowStyle) {
        this(anchor, tag, implicit, flowStyle, Optional.empty(), Optional.empty());
    }

    @Override
    public ID getEventId() {
        return ID.SequenceStart;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("+SEQ");
        if (getFlowStyle() == FlowStyle.FLOW) {
            //TODO builder.append(" []");
        }
        builder.append(super.toString());
        return builder.toString();
    }
}
