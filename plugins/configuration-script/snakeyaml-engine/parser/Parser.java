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
package org.snakeyaml.engine.v1.parser;


import java.util.Iterator;

import org.snakeyaml.engine.v1.events.Event;
import org.snakeyaml.engine.v1.exceptions.ParserException;

/**
 * This interface represents an input stream of {@link Event Events}.
 * <p>
 * The parser and the scanner form together the 'Parse' step in the loading
 * process.
 * </p>
 *
 * @see org.snakeyaml.engine.v1.events.Event
 */
public interface Parser extends Iterator<Event> {

    /**
     * Check if the next event is one of the given type.
     *
     * @param choice Event ID.
     * @return <code>true</code> if the next event can be assigned to a variable
     * of the given type. Returns <code>false</code> if no more events
     * are available.
     * @throws ParserException Thrown in case of malformed input.
     */
    boolean checkEvent(Event.ID choice);

    /**
     * Return the next event, but do not delete it from the stream.
     *
     * @return The event that will be returned on the next call to
     * {@link #next}
     * @throws ParserException Thrown in case of malformed input.
     */
    Event peekEvent();

    /**
     * Returns the next event.
     * <p>
     * The event will be removed from the stream.
     * </p>
     *
     * @return the next parsed event
     * @throws ParserException Thrown in case of malformed input.
     */
    Event next();
}
