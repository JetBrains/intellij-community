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

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Objects;

import org.snakeyaml.engine.v1.api.LoadSettings;
import org.snakeyaml.engine.v1.api.YamlUnicodeReader;
import org.snakeyaml.engine.v1.events.Event;
import org.snakeyaml.engine.v1.parser.ParserImpl;
import org.snakeyaml.engine.v1.scanner.StreamReader;

/**
 * Read the input stream and parse the content into events (opposite for Present or Emit)
 */
public class Parse {

    private LoadSettings settings;

    /**
     * Create instance with provided {@link LoadSettings}
     *
     * @param settings - configuration
     */
    public Parse(LoadSettings settings) {
        Objects.requireNonNull(settings, "LoadSettings cannot be null");
        this.settings = settings;
    }

    /**
     * Parse a YAML stream and produce parsing events.
     *
     * @param yaml - YAML document(s). Default encoding is UTF-8. The BOM must be present if the encoding is UTF-16 or UTF-32
     * @return parsed events
     * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">Processing Overview</a>
     */
    public Iterable<Event> parseInputStream(InputStream yaml) {
        Objects.requireNonNull(yaml, "InputStream cannot be null");
        return () -> new ParserImpl(new StreamReader(new YamlUnicodeReader(yaml), settings), settings);
    }

    /**
     * Parse a YAML stream and produce parsing events. Since the encoding is already known the BOM must not be present
     * (it will be parsed as content)
     *
     * @param yaml - YAML document(s).
     * @return parsed events
     * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">Processing Overview</a>
     */
    public Iterable<Event> parseReader(Reader yaml) {
        Objects.requireNonNull(yaml, "Reader cannot be null");
        return () -> new ParserImpl(new StreamReader(yaml, settings), settings);
    }

    /**
     * Parse a YAML stream and produce parsing events.
     *
     * @param yaml - YAML document(s). The BOM must not be present (it will be parsed as content)
     * @return parsed events
     * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">Processing Overview</a>
     */
    public Iterable<Event> parseString(String yaml) {
        Objects.requireNonNull(yaml, "String cannot be null");
        //do not use lambda to keep Iterable and Iterator visible
        return new Iterable() {
            public Iterator<Event> iterator() {
                return new ParserImpl(new StreamReader(new StringReader(yaml), settings), settings);
            }
        };
    }
}

