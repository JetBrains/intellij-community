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
package org.snakeyaml.engine.v1.api.lowlevel;

import java.io.StringWriter;
import java.util.Iterator;
import java.util.Objects;

import org.snakeyaml.engine.v1.api.DumpSettings;
import org.snakeyaml.engine.v1.api.StreamDataWriter;
import org.snakeyaml.engine.v1.emitter.Emitter;
import org.snakeyaml.engine.v1.events.Event;

/**
 * Emit the events into a data stream (opposite for Parse)
 */
public class Present {

    private final DumpSettings settings;

    /**
     * Create Present (emitter)
     *
     * @param settings - configuration
     */
    public Present(DumpSettings settings) {
        Objects.requireNonNull(settings, "DumpSettings cannot be null");
        this.settings = settings;
    }

    public String emitToString(Iterator<Event> events) {
        Objects.requireNonNull(events, "events cannot be null");
        YamlStringWriterStream writer = new YamlStringWriterStream();
        final Emitter emitter = new Emitter(settings, writer);
        events.forEachRemaining(emitter::emit);
        return writer.getString();
    }

}

class YamlStringWriterStream implements StreamDataWriter {
    StringWriter writer = new StringWriter();

    @Override
    public void write(String str) {
        writer.write(str);
    }

    @Override
    public void write(String str, int off, int len) {
        writer.write(str, off, len);
    }

    public String getString() {
        return writer.toString();
    }
}

