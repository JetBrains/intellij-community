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
package org.snakeyaml.engine.v1.emitter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Pattern;

import org.snakeyaml.engine.v1.api.DumpSettings;
import org.snakeyaml.engine.v1.api.StreamDataWriter;
import org.snakeyaml.engine.v1.common.Anchor;
import org.snakeyaml.engine.v1.common.ArrayStack;
import org.snakeyaml.engine.v1.common.CharConstants;
import org.snakeyaml.engine.v1.common.ScalarStyle;
import org.snakeyaml.engine.v1.common.SpecVersion;
import org.snakeyaml.engine.v1.events.CollectionEndEvent;
import org.snakeyaml.engine.v1.events.CollectionStartEvent;
import org.snakeyaml.engine.v1.events.DocumentEndEvent;
import org.snakeyaml.engine.v1.events.DocumentStartEvent;
import org.snakeyaml.engine.v1.events.Event;
import org.snakeyaml.engine.v1.events.MappingStartEvent;
import org.snakeyaml.engine.v1.events.NodeEvent;
import org.snakeyaml.engine.v1.events.ScalarEvent;
import org.snakeyaml.engine.v1.events.SequenceStartEvent;
import org.snakeyaml.engine.v1.exceptions.EmitterException;
import org.snakeyaml.engine.v1.exceptions.YamlEngineException;
import org.snakeyaml.engine.v1.nodes.Tag;
import org.snakeyaml.engine.v1.scanner.StreamReader;


/**
 * <pre>
 * Emitter expects events obeying the following grammar:
 * stream ::= STREAM-START document* STREAM-END
 * document ::= DOCUMENT-START node DOCUMENT-END
 * node ::= SCALAR | sequence | mapping
 * sequence ::= SEQUENCE-START node* SEQUENCE-END
 * mapping ::= MAPPING-START (node node)* MAPPING-END
 * </pre>
 */
public final class Emitter implements Emitable {
    private static final Map<Character, String> ESCAPE_REPLACEMENTS = new HashMap();
    public static final int MIN_INDENT = 1;
    public static final int MAX_INDENT = 10;

    private static final String SPACE = " ";

    static {
        ESCAPE_REPLACEMENTS.put('\0', "0");
        ESCAPE_REPLACEMENTS.put('\u0007', "a");
        ESCAPE_REPLACEMENTS.put('\u0008', "b");
        ESCAPE_REPLACEMENTS.put('\u0009', "t");
        ESCAPE_REPLACEMENTS.put('\n', "n");
        ESCAPE_REPLACEMENTS.put('\u000B', "v");
        ESCAPE_REPLACEMENTS.put('\u000C', "f");
        ESCAPE_REPLACEMENTS.put('\r', "r");
        ESCAPE_REPLACEMENTS.put('\u001B', "e");
        ESCAPE_REPLACEMENTS.put('"', "\"");
        ESCAPE_REPLACEMENTS.put('\\', "\\");
        ESCAPE_REPLACEMENTS.put('\u0085', "N");
        ESCAPE_REPLACEMENTS.put('\u00A0', "_");
        ESCAPE_REPLACEMENTS.put('\u2028', "L");
        ESCAPE_REPLACEMENTS.put('\u2029', "P");
    }

    private final static Map<String, String> DEFAULT_TAG_PREFIXES = new LinkedHashMap<String, String>();

    static {
        DEFAULT_TAG_PREFIXES.put("!", "!");
        DEFAULT_TAG_PREFIXES.put(Tag.PREFIX, "!!");
    }

    // The stream should have the methods `write` and possibly `flush`.
    private final StreamDataWriter stream;

    // Encoding is defined by Writer (cannot be overriden by STREAM-START.)
    // private Charset encoding;

    // Emitter is a state machine with a stack of states to handle nested
    // structures.
    private final ArrayStack<EmitterState> states;
    private EmitterState state;

    // Current event and the event queue.
    private final Queue<Event> events;
    private Event event;

    // The current indentation level and the stack of previous indents.
    private final ArrayStack<Integer> indents;
    private Integer indent;

    // Flow level.
    private int flowLevel;

    // Contexts.
    private boolean rootContext;
    private boolean mappingContext;
    private boolean simpleKeyContext;

    //
    // Characteristics of the last emitted character:
    // - current position.
    // - is it a whitespace?
    // - is it an indention character
    // (indentation space, '-', '?', or ':')?
    // private int line; this variable is not used
    private int column;
    private boolean whitespace;
    private boolean indention;
    private boolean openEnded;

    // Formatting details.
    private Boolean canonical;
    // pretty print flow by adding extra line breaks
    private Boolean multiLineFlow;

    private boolean allowUnicode;
    private int bestIndent;
    private int indicatorIndent;
    private int bestWidth;
    private String bestLineBreak;
    private boolean splitLines;
    private int maxSimpleKeyLength;

    // Tag prefixes.
    private Map<String, String> tagPrefixes;

    // Prepared anchor and tag.
    private Optional<Anchor> preparedAnchor;
    private String preparedTag;

    // Scalar analysis and style.
    private ScalarAnalysis analysis;
    private Optional<ScalarStyle> scalarStyle;

    public Emitter(DumpSettings opts, StreamDataWriter stream) {
        // The stream should have the methods `write` and possibly `flush`.
        this.stream = stream;
        // Emitter is a state machine with a stack of states to handle nested
        // structures.
        this.states = new ArrayStack(100);
        this.state = new ExpectStreamStart();
        // Current event and the event queue.
        this.events = new ArrayBlockingQueue(100);
        this.event = null;
        // The current indentation level and the stack of previous indents.
        this.indents = new ArrayStack(10);
        this.indent = null;
        // Flow level.
        this.flowLevel = 0;
        // Contexts.
        mappingContext = false;
        simpleKeyContext = false;

        //
        // Characteristics of the last emitted character:
        // - current position.
        // - is it a whitespace?
        // - is it an indention character
        // (indentation space, '-', '?', or ':')?
        column = 0;
        whitespace = true;
        indention = true;

        // Whether the document requires an explicit document indicator
        openEnded = false;

        // Formatting details.
        this.canonical = opts.isCanonical();
        this.multiLineFlow = opts.isMultiLineFlow();
        this.allowUnicode = opts.isUseUnicodeEncoding();
        this.bestIndent = 2;
        if ((opts.getIndent() > MIN_INDENT) && (opts.getIndent() < MAX_INDENT)) {
            this.bestIndent = opts.getIndent();
        }
        this.indicatorIndent = opts.getIndicatorIndent();
        this.bestWidth = 80;
        if (opts.getWidth() > this.bestIndent * 2) {
            this.bestWidth = opts.getWidth();
        }
        this.bestLineBreak = opts.getBestLineBreak();
        this.splitLines = opts.isSplitLines();
        this.maxSimpleKeyLength = opts.getMaxSimpleKeyLength();

        // Tag prefixes.
        this.tagPrefixes = new LinkedHashMap();

        // Prepared anchor and tag.
        this.preparedAnchor = Optional.empty();
        this.preparedTag = null;

        // Scalar analysis and style.
        this.analysis = null;
        this.scalarStyle = Optional.empty();
    }

    public void emit(Event event) {
        this.events.add(event);
        while (!needMoreEvents()) {
            this.event = this.events.poll();
            this.state.expect();
            this.event = null;
        }
    }

    // In some cases, we wait for a few next events before emitting.

    private boolean needMoreEvents() {
        if (events.isEmpty()) {
            return true;
        }
        Event event = events.peek();
        if (event.isEvent(Event.ID.DocumentStart)) {
            return needEvents(1);
        } else if (event.isEvent(Event.ID.SequenceStart)) {
            return needEvents(2);
        } else if (event.isEvent(Event.ID.MappingStart)) {
            return needEvents(3);
        } else {
            return false;
        }
    }

    private boolean needEvents(int count) {
        int level = 0;
        Iterator<Event> iter = events.iterator();
        iter.next();
        while (iter.hasNext()) {
            Event event = iter.next();
            if (event.isEvent(Event.ID.DocumentStart) || event instanceof CollectionStartEvent) {
                level++;
            } else if (event.isEvent(Event.ID.DocumentEnd) || event instanceof CollectionEndEvent) {
                level--;
            } else if (event.isEvent(Event.ID.StreamEnd)) {
                level = -1;
            }
            if (level < 0) {
                return false;
            }
        }
        return events.size() < count + 1;
    }

    private void increaseIndent(boolean flow, boolean indentless) {
        indents.push(indent);
        if (indent == null) {
            if (flow) {
                indent = bestIndent;
            } else {
                indent = 0;
            }
        } else if (!indentless) {
            this.indent += bestIndent;
        }
    }

    // States

    // Stream handlers.

    private class ExpectStreamStart implements EmitterState {
        public void expect() {
            if (event.isEvent(Event.ID.StreamStart)) {
                writeStreamStart();
                state = new ExpectFirstDocumentStart();
            } else {
                throw new EmitterException("expected StreamStartEvent, but got " + event);
            }
        }
    }

    private class ExpectNothing implements EmitterState {
        public void expect() {
            throw new EmitterException("expecting nothing, but got " + event);
        }
    }

    // Document handlers.

    private class ExpectFirstDocumentStart implements EmitterState {
        public void expect() {
            new ExpectDocumentStart(true).expect();
        }
    }

    private class ExpectDocumentStart implements EmitterState {
        private boolean first;

        public ExpectDocumentStart(boolean first) {
            this.first = first;
        }

        public void expect() {
            if (event.isEvent(Event.ID.DocumentStart)) {
                DocumentStartEvent ev = (DocumentStartEvent) event;
                if ((ev.getSpecVersion().isPresent() || ev.getTags() != null) && openEnded) {
                    writeIndicator("...", true, false, false);
                    writeIndent();
                }
                ev.getSpecVersion().ifPresent(version -> writeVersionDirective(prepareVersion(version)));
                tagPrefixes = new LinkedHashMap(DEFAULT_TAG_PREFIXES);
                if (ev.getTags() != null) {
                    Set<String> handles = new TreeSet(ev.getTags().keySet());
                    for (String handle : handles) {
                        String prefix = ev.getTags().get(handle);
                        tagPrefixes.put(prefix, handle);
                        String handleText = prepareTagHandle(handle);
                        String prefixText = prepareTagPrefix(prefix);
                        writeTagDirective(handleText, prefixText);
                    }
                }
                boolean implicit = first && !ev.isExplicit() && !canonical
                        && !ev.getSpecVersion().isPresent()
                        && (ev.getTags() == null || ev.getTags().isEmpty())
                        && !checkEmptyDocument();
                if (!implicit) {
                    writeIndent();
                    writeIndicator("---", true, false, false);
                    if (canonical) {
                        writeIndent();
                    }
                }
                state = new ExpectDocumentRoot();
            } else if (event.getEventId() == Event.ID.StreamEnd) {
                writeStreamEnd();
                state = new ExpectNothing();
            } else {
                throw new EmitterException("expected DocumentStartEvent, but got " + event);
            }
        }
    }

    private class ExpectDocumentEnd implements EmitterState {
        public void expect() {
            if (event.getEventId() == Event.ID.DocumentEnd) {
                writeIndent();
                if (((DocumentEndEvent) event).isExplicit()) {
                    writeIndicator("...", true, false, false);
                    writeIndent();
                }
                flushStream();
                state = new ExpectDocumentStart(false);
            } else {
                throw new EmitterException("expected DocumentEndEvent, but got " + event);
            }
        }
    }

    private class ExpectDocumentRoot implements EmitterState {
        public void expect() {
            states.push(new ExpectDocumentEnd());
            expectNode(true, false, false);
        }
    }

    // Node handlers.

    private void expectNode(boolean root, boolean mapping, boolean simpleKey) {
        rootContext = root;
        mappingContext = mapping;
        simpleKeyContext = simpleKey;
        if (event.isEvent(Event.ID.Alias)) {
            expectAlias();
        } else if (event.isEvent(Event.ID.Scalar) || event instanceof CollectionStartEvent) {
            processAnchor("&");
            processTag();
            if (event.isEvent(Event.ID.Scalar)) {
                expectScalar();
            } else if (event.isEvent(Event.ID.SequenceStart)) {
                if (flowLevel != 0 || canonical || ((SequenceStartEvent) event).isFlow()
                        || checkEmptySequence()) {
                    expectFlowSequence();
                } else {
                    expectBlockSequence();
                }
            } else {// MappingStartEvent
                if (flowLevel != 0 || canonical || ((MappingStartEvent) event).isFlow()
                        || checkEmptyMapping()) {
                    expectFlowMapping();
                } else {
                    expectBlockMapping();
                }
            }
        } else {
            throw new EmitterException("expected NodeEvent, but got " + event);
        }
    }

    private void expectAlias() {
        if (((NodeEvent) event).getAnchor() == null) {
            throw new EmitterException("anchor is not specified for alias");
        }
        processAnchor("*");
        state = states.pop();
    }

    private void expectScalar() {
        increaseIndent(true, false);
        processScalar();
        indent = indents.pop();
        state = states.pop();
    }

    // Flow sequence handlers.

    private void expectFlowSequence() {
        writeIndicator("[", true, true, false);
        flowLevel++;
        increaseIndent(true, false);
        if (multiLineFlow) {
            writeIndent();
        }
        state = new ExpectFirstFlowSequenceItem();
    }

    private class ExpectFirstFlowSequenceItem implements EmitterState {
        public void expect() {
            if (event.isEvent(Event.ID.SequenceEnd)) {
                indent = indents.pop();
                flowLevel--;
                writeIndicator("]", false, false, false);
                state = states.pop();
            } else {
                if (canonical || (column > bestWidth && splitLines) || multiLineFlow) {
                    writeIndent();
                }
                states.push(new ExpectFlowSequenceItem());
                expectNode(false, false, false);
            }
        }
    }

    private class ExpectFlowSequenceItem implements EmitterState {
        public void expect() {
            if (event.isEvent(Event.ID.SequenceEnd)) {
                indent = indents.pop();
                flowLevel--;
                if (canonical) {
                    writeIndicator(",", false, false, false);
                    writeIndent();
                }
                writeIndicator("]", false, false, false);
                if (multiLineFlow) {
                    writeIndent();
                }
                state = states.pop();
            } else {
                writeIndicator(",", false, false, false);
                if (canonical || (column > bestWidth && splitLines) || multiLineFlow) {
                    writeIndent();
                }
                states.push(new ExpectFlowSequenceItem());
                expectNode(false, false, false);
            }
        }
    }

    // Flow mapping handlers.

    private void expectFlowMapping() {
        writeIndicator("{", true, true, false);
        flowLevel++;
        increaseIndent(true, false);
        if (multiLineFlow) {
            writeIndent();
        }
        state = new ExpectFirstFlowMappingKey();
    }

    private class ExpectFirstFlowMappingKey implements EmitterState {
        public void expect() {
            if (event.isEvent(Event.ID.MappingEnd)) {
                indent = indents.pop();
                flowLevel--;
                writeIndicator("}", false, false, false);
                state = states.pop();
            } else {
                if (canonical || (column > bestWidth && splitLines) || multiLineFlow) {
                    writeIndent();
                }
                if (!canonical && checkSimpleKey()) {
                    states.push(new ExpectFlowMappingSimpleValue());
                    expectNode(false, true, true);
                } else {
                    writeIndicator("?", true, false, false);
                    states.push(new ExpectFlowMappingValue());
                    expectNode(false, true, false);
                }
            }
        }
    }

    private class ExpectFlowMappingKey implements EmitterState {
        public void expect() {
            if (event.isEvent(Event.ID.MappingEnd)) {
                indent = indents.pop();
                flowLevel--;
                if (canonical) {
                    writeIndicator(",", false, false, false);
                    writeIndent();
                }
                if (multiLineFlow) {
                    writeIndent();
                }
                writeIndicator("}", false, false, false);
                state = states.pop();
            } else {
                writeIndicator(",", false, false, false);
                if (canonical || (column > bestWidth && splitLines) || multiLineFlow) {
                    writeIndent();
                }
                if (!canonical && checkSimpleKey()) {
                    states.push(new ExpectFlowMappingSimpleValue());
                    expectNode(false, true, true);
                } else {
                    writeIndicator("?", true, false, false);
                    states.push(new ExpectFlowMappingValue());
                    expectNode(false, true, false);
                }
            }
        }
    }

    private class ExpectFlowMappingSimpleValue implements EmitterState {
        public void expect() {
            writeIndicator(":", false, false, false);
            states.push(new ExpectFlowMappingKey());
            expectNode(false, true, false);
        }
    }

    private class ExpectFlowMappingValue implements EmitterState {
        public void expect() {
            if (canonical || (column > bestWidth) || multiLineFlow) {
                writeIndent();
            }
            writeIndicator(":", true, false, false);
            states.push(new ExpectFlowMappingKey());
            expectNode(false, true, false);
        }
    }

    // Block sequence handlers.

    private void expectBlockSequence() {
        boolean indentless = mappingContext && !indention;
        increaseIndent(false, indentless);
        state = new ExpectFirstBlockSequenceItem();
    }

    private class ExpectFirstBlockSequenceItem implements EmitterState {
        public void expect() {
            new ExpectBlockSequenceItem(true).expect();
        }
    }

    private class ExpectBlockSequenceItem implements EmitterState {
        private boolean first;

        public ExpectBlockSequenceItem(boolean first) {
            this.first = first;
        }

        public void expect() {
            if (!this.first && event.isEvent(Event.ID.SequenceEnd)) {
                indent = indents.pop();
                state = states.pop();
            } else {
                writeIndent();
                writeWhitespace(indicatorIndent);
                writeIndicator("-", true, false, true);
                states.push(new ExpectBlockSequenceItem(false));
                expectNode(false, false, false);
            }
        }
    }

    // Block mapping handlers.
    private void expectBlockMapping() {
        increaseIndent(false, false);
        state = new ExpectFirstBlockMappingKey();
    }

    private class ExpectFirstBlockMappingKey implements EmitterState {
        public void expect() {
            new ExpectBlockMappingKey(true).expect();
        }
    }

    private class ExpectBlockMappingKey implements EmitterState {
        private boolean first;

        public ExpectBlockMappingKey(boolean first) {
            this.first = first;
        }

        public void expect() {
            if (!this.first && event.isEvent(Event.ID.MappingEnd)) {
                indent = indents.pop();
                state = states.pop();
            } else {
                writeIndent();
                if (checkSimpleKey()) {
                    states.push(new ExpectBlockMappingSimpleValue());
                    expectNode(false, true, true);
                } else {
                    writeIndicator("?", true, false, true);
                    states.push(new ExpectBlockMappingValue());
                    expectNode(false, true, false);
                }
            }
        }
    }

    private class ExpectBlockMappingSimpleValue implements EmitterState {
        public void expect() {
            writeIndicator(":", false, false, false);
            states.push(new ExpectBlockMappingKey(false));
            expectNode(false, true, false);
        }
    }

    private class ExpectBlockMappingValue implements EmitterState {
        public void expect() {
            writeIndent();
            writeIndicator(":", true, false, true);
            states.push(new ExpectBlockMappingKey(false));
            expectNode(false, true, false);
        }
    }

    // Checkers.

    private boolean checkEmptySequence() {
        return event.isEvent(Event.ID.SequenceStart) && !events.isEmpty() && events.peek().isEvent(Event.ID.SequenceEnd);
    }

    private boolean checkEmptyMapping() {
        return event.isEvent(Event.ID.MappingStart) && !events.isEmpty() && events.peek().isEvent(Event.ID.MappingEnd);
    }

    private boolean checkEmptyDocument() {
        if (!(event.isEvent(Event.ID.DocumentStart)) || events.isEmpty()) {
            return false;
        }
        Event event = events.peek();
        if (event.isEvent(Event.ID.Scalar)) {
            ScalarEvent e = (ScalarEvent) event;
            return e.getAnchor() == null && e.getTag() == null && e.getImplicit() != null && e
                    .getValue().length() == 0;
        }
        return false;
    }

    private boolean checkSimpleKey() {
        int length = 0;
        if (event instanceof NodeEvent && ((NodeEvent) event).getAnchor().isPresent()) {
            if (!preparedAnchor.isPresent()) {
                preparedAnchor = ((NodeEvent) event).getAnchor();
            }
            length += preparedAnchor.get().getAnchor().length();
        }
        Optional<String> tag = Optional.empty();
        if (event.isEvent(Event.ID.Scalar)) {
            tag = ((ScalarEvent) event).getTag();
        } else if (event instanceof CollectionStartEvent) {
            tag = ((CollectionStartEvent) event).getTag();
        }
        if (tag.isPresent()) {
            if (preparedTag == null) {
                preparedTag = prepareTag(tag.get());
            }
            length += preparedTag.length();
        }
        if (event.isEvent(Event.ID.Scalar)) {
            if (analysis == null) {
                analysis = analyzeScalar(((ScalarEvent) event).getValue());
            }
            length += analysis.scalar.length();
        }
        return length < maxSimpleKeyLength && (event.isEvent(Event.ID.Alias)
                || (event.isEvent(Event.ID.Scalar) && !analysis.empty && !analysis.multiline)
                || checkEmptySequence() || checkEmptyMapping());
    }

    // Anchor, Tag, and Scalar processors.

    private void processAnchor(String indicator) {
        NodeEvent ev = (NodeEvent) event;
        if (!ev.getAnchor().isPresent()) {
            preparedAnchor = Optional.empty();
            return;
        } else {
            if (!preparedAnchor.isPresent()) {
                preparedAnchor = ev.getAnchor();
            }
            writeIndicator(indicator + preparedAnchor.get(), true, false, false);
            preparedAnchor = Optional.empty();
        }
    }

    private void processTag() {
        Optional<String> tag;
        if (event.isEvent(Event.ID.Scalar)) {
            ScalarEvent ev = (ScalarEvent) event;
            tag = ev.getTag();
            if (!scalarStyle.isPresent()) {
                scalarStyle = chooseScalarStyle();
            }
            if ((!canonical || !tag.isPresent()) && ((!scalarStyle.isPresent() && ev.getImplicit()
                    .canOmitTagInPlainScalar()) || (scalarStyle.isPresent() && ev.getImplicit()
                    .canOmitTagInNonPlainScalar()))) {
                preparedTag = null;
                return;
            }
            if (ev.getImplicit().canOmitTagInPlainScalar() && !tag.isPresent()) {
                tag = Optional.of("!");
                preparedTag = null;
            }
        } else {
            CollectionStartEvent ev = (CollectionStartEvent) event;
            tag = ev.getTag();
            if ((!canonical || !tag.isPresent()) && ev.isImplicit()) {
                preparedTag = null;
                return;
            }
        }
        if (!tag.isPresent()) {
            throw new EmitterException("tag is not specified");
        }
        if (preparedTag == null) {
            preparedTag = prepareTag(tag.get());
        }
        writeIndicator(preparedTag, true, false, false);
        preparedTag = null;
    }

    private Optional<ScalarStyle> chooseScalarStyle() {
        ScalarEvent ev = (ScalarEvent) event;
        if (analysis == null) {
            analysis = analyzeScalar(ev.getValue());
        }
        if (!ev.isPlain() && ev.getScalarStyle() == ScalarStyle.DOUBLE_QUOTED || this.canonical) {
            return Optional.of(ScalarStyle.DOUBLE_QUOTED);
        }
        if (ev.isPlain() && ev.getImplicit().canOmitTagInPlainScalar()) {
            if (!(simpleKeyContext && (analysis.empty || analysis.multiline))
                    && ((flowLevel != 0 && analysis.allowFlowPlain) || (flowLevel == 0 && analysis.allowBlockPlain))) {
                return Optional.empty();
            }
        }
        if (!ev.isPlain() && (ev.getScalarStyle() == ScalarStyle.LITERAL || ev.getScalarStyle() == ScalarStyle.FOLDED)) {
            if (flowLevel == 0 && !simpleKeyContext && analysis.allowBlock) {
                return Optional.of(ev.getScalarStyle());
            }
        }
        if (ev.isPlain() || ev.getScalarStyle() == ScalarStyle.SINGLE_QUOTED) {
            if (analysis.allowSingleQuoted && !(simpleKeyContext && analysis.multiline)) {
                return Optional.of(ScalarStyle.SINGLE_QUOTED);
            }
        }
        return Optional.of(ScalarStyle.DOUBLE_QUOTED);
    }

    private void processScalar() {
        ScalarEvent ev = (ScalarEvent) event;
        if (analysis == null) {
            analysis = analyzeScalar(ev.getValue());
        }
        if (!scalarStyle.isPresent()) {
            scalarStyle = chooseScalarStyle();
        }
        boolean split = !simpleKeyContext && splitLines;
        if (!scalarStyle.isPresent()) {
            writePlain(analysis.scalar, split);
        } else {
            switch (scalarStyle.get()) {
                case DOUBLE_QUOTED:
                    writeDoubleQuoted(analysis.scalar, split);
                    break;
                case SINGLE_QUOTED:
                    writeSingleQuoted(analysis.scalar, split);
                    break;
                case FOLDED:
                    writeFolded(analysis.scalar, split);
                    break;
                case LITERAL:
                    writeLiteral(analysis.scalar);
                    break;
                default:
                    throw new YamlEngineException("Unexpected scalarStyle: " + scalarStyle);
            }
        }
        analysis = null;
        scalarStyle = Optional.empty();
    }

    // Analyzers.

    private String prepareVersion(SpecVersion version) {
        if (version.getMajor() != 1) {
            throw new EmitterException("unsupported YAML version: " + version);
        }
        return version.getRepresentation();
    }

    private final static Pattern HANDLE_FORMAT = Pattern.compile("^![-_\\w]*!$");

    private String prepareTagHandle(String handle) {
        if (handle.length() == 0) {
            throw new EmitterException("tag handle must not be empty");
        } else if (handle.charAt(0) != '!' || handle.charAt(handle.length() - 1) != '!') {
            throw new EmitterException("tag handle must start and end with '!': " + handle);
        } else if (!"!".equals(handle) && !HANDLE_FORMAT.matcher(handle).matches()) {
            throw new EmitterException("invalid character in the tag handle: " + handle);
        }
        return handle;
    }

    private String prepareTagPrefix(String prefix) {
        if (prefix.length() == 0) {
            throw new EmitterException("tag prefix must not be empty");
        }
        StringBuilder chunks = new StringBuilder();
        int start = 0;
        int end = 0;
        if (prefix.charAt(0) == '!') {
            end = 1;
        }
        while (end < prefix.length()) {
            end++;
        }
        if (start < end) {
            chunks.append(prefix.substring(start, end));
        }
        return chunks.toString();
    }

    private String prepareTag(String tag) {
        if (tag.length() == 0) {
            throw new EmitterException("tag must not be empty");
        }
        if ("!".equals(tag)) {
            return tag;
        }
        String handle = null;
        String suffix = tag;
        // shall the tag prefixes be sorted as in PyYAML?
        for (String prefix : tagPrefixes.keySet()) {
            if (tag.startsWith(prefix) && ("!".equals(prefix) || prefix.length() < tag.length())) {
                handle = prefix;
            }
        }
        if (handle != null) {
            suffix = tag.substring(handle.length());
            handle = tagPrefixes.get(handle);
        }

        int end = suffix.length();
        String suffixText = end > 0 ? suffix.substring(0, end) : "";

        if (handle != null) {
            return handle + suffixText;
        }
        return "!<" + suffixText + ">";
    }


    private ScalarAnalysis analyzeScalar(String scalar) {
        // Empty scalar is a special case.
        if (scalar.length() == 0) {
            return new ScalarAnalysis(scalar, true, false, false, true, true, false);
        }
        // Indicators and special characters.
        boolean blockIndicators = false;
        boolean flowIndicators = false;
        boolean lineBreaks = false;
        boolean specialCharacters = false;

        // Important whitespace combinations.
        boolean leadingSpace = false;
        boolean leadingBreak = false;
        boolean trailingSpace = false;
        boolean trailingBreak = false;
        boolean breakSpace = false;
        boolean spaceBreak = false;

        // Check document indicators.
        if (scalar.startsWith("---") || scalar.startsWith("...")) {
            blockIndicators = true;
            flowIndicators = true;
        }
        // First character or preceded by a whitespace.
        boolean preceededByWhitespace = true;
        boolean followedByWhitespace = scalar.length() == 1 || CharConstants.NULL_BL_T_LINEBR.has(scalar.codePointAt(1));
        // The previous character is a space.
        boolean previousSpace = false;

        // The previous character is a break.
        boolean previousBreak = false;

        int index = 0;

        while (index < scalar.length()) {
            int c = scalar.codePointAt(index);
            // Check for indicators.
            if (index == 0) {
                // Leading indicators are special characters.
                if ("#,[]{}&*!|>\'\"%@`".indexOf(c) != -1) {
                    flowIndicators = true;
                    blockIndicators = true;
                }
                if (c == '?' || c == ':') {
                    flowIndicators = true;
                    if (followedByWhitespace) {
                        blockIndicators = true;
                    }
                }
                if (c == '-' && followedByWhitespace) {
                    flowIndicators = true;
                    blockIndicators = true;
                }
            } else {
                // Some indicators cannot appear within a scalar as well.
                if (",?[]{}".indexOf(c) != -1) {
                    flowIndicators = true;
                }
                if (c == ':') {
                    flowIndicators = true;
                    if (followedByWhitespace) {
                        blockIndicators = true;
                    }
                }
                if (c == '#' && preceededByWhitespace) {
                    flowIndicators = true;
                    blockIndicators = true;
                }
            }
            // Check for line breaks, special, and unicode characters.
            boolean isLineBreak = CharConstants.LINEBR.has(c);
            if (isLineBreak) {
                lineBreaks = true;
            }
            if (!(c == '\n' || (0x20 <= c && c <= 0x7E))) {
                if (c == 0x85 || (c >= 0xA0 && c <= 0xD7FF)
                        || (c >= 0xE000 && c <= 0xFFFD)
                        || (c >= 0x10000 && c <= 0x10FFFF)) {
                    // unicode is used
                    if (!this.allowUnicode) {
                        specialCharacters = true;
                    }
                } else {
                    specialCharacters = true;
                }
            }
            // Detect important whitespace combinations.
            if (c == ' ') {
                if (index == 0) {
                    leadingSpace = true;
                }
                if (index == scalar.length() - 1) {
                    trailingSpace = true;
                }
                if (previousBreak) {
                    breakSpace = true;
                }
                previousSpace = true;
                previousBreak = false;
            } else if (isLineBreak) {
                if (index == 0) {
                    leadingBreak = true;
                }
                if (index == scalar.length() - 1) {
                    trailingBreak = true;
                }
                if (previousSpace) {
                    spaceBreak = true;
                }
                previousSpace = false;
                previousBreak = true;
            } else {
                previousSpace = false;
                previousBreak = false;
            }

            // Prepare for the next character.
            index += Character.charCount(c);
            preceededByWhitespace = CharConstants.NULL_BL_T.has(c) || isLineBreak;
            followedByWhitespace = true;
            if (index + 1 < scalar.length()) {
                int nextIndex = index + Character.charCount(scalar.codePointAt(index));
                if (nextIndex < scalar.length()) {
                    followedByWhitespace = (CharConstants.NULL_BL_T.has(scalar.codePointAt(nextIndex))) || isLineBreak;
                }
            }
        }
        // Let's decide what styles are allowed.
        boolean allowFlowPlain = true;
        boolean allowBlockPlain = true;
        boolean allowSingleQuoted = true;
        boolean allowBlock = true;
        // Leading and trailing whitespaces are bad for plain scalars.
        if (leadingSpace || leadingBreak || trailingSpace || trailingBreak) {
            allowFlowPlain = allowBlockPlain = false;
        }
        // We do not permit trailing spaces for block scalars.
        if (trailingSpace) {
            allowBlock = false;
        }
        // Spaces at the beginning of a new line are only acceptable for block
        // scalars.
        if (breakSpace) {
            allowFlowPlain = allowBlockPlain = allowSingleQuoted = false;
        }
        // Spaces followed by breaks, as well as special character are only
        // allowed for double quoted scalars.
        if (spaceBreak || specialCharacters) {
            allowFlowPlain = allowBlockPlain = allowSingleQuoted = allowBlock = false;
        }
        // Although the plain scalar writer supports breaks, we never emit
        // multiline plain scalars in the flow context.
        if (lineBreaks) {
            allowFlowPlain = false;
        }
        // Flow indicators are forbidden for flow plain scalars.
        if (flowIndicators) {
            allowFlowPlain = false;
        }
        // Block indicators are forbidden for block plain scalars.
        if (blockIndicators) {
            allowBlockPlain = false;
        }

        return new ScalarAnalysis(scalar, false, lineBreaks, allowFlowPlain, allowBlockPlain,
                allowSingleQuoted, allowBlock);
    }

    // Writers.

    void flushStream() {
        stream.flush();
    }

    void writeStreamStart() {
        // BOM is written by Writer.
    }

    void writeStreamEnd() {
        flushStream();
    }

    void writeIndicator(String indicator, boolean needWhitespace, boolean whitespace,
                        boolean indentation) {
        if (!this.whitespace && needWhitespace) {
            this.column++;
            stream.write(SPACE);
        }
        this.whitespace = whitespace;
        this.indention = this.indention && indentation;
        this.column += indicator.length();
        openEnded = false;
        stream.write(indicator);
    }

    void writeIndent() {
        int indent;
        if (this.indent != null) {
            indent = this.indent;
        } else {
            indent = 0;
        }

        if (!this.indention || this.column > indent || (this.column == indent && !this.whitespace)) {
            writeLineBreak(null);
        }

        writeWhitespace(indent - this.column);
    }

    private void writeWhitespace(int length) {
        if (length <= 0) {
            return;
        }
        this.whitespace = true;
        for (int i = 0; i < length; i++) {
            stream.write(" ");
        }
        this.column += length;
    }

    private void writeLineBreak(String data) {
        this.whitespace = true;
        this.indention = true;
        this.column = 0;
        if (data == null) {
            stream.write(this.bestLineBreak);
        } else {
            stream.write(data);
        }
    }

    void writeVersionDirective(String versionText) {
        stream.write("%YAML ");
        stream.write(versionText);
        writeLineBreak(null);
    }

    void writeTagDirective(String handleText, String prefixText) {
        // XXX: not sure 4 invocations better then StringBuilders created by str
        // + str
        stream.write("%TAG ");
        stream.write(handleText);
        stream.write(SPACE);
        stream.write(prefixText);
        writeLineBreak(null);
    }

    // Scalar streams.
    private void writeSingleQuoted(String text, boolean split) {
        writeIndicator("'", true, false, false);
        boolean spaces = false;
        boolean breaks = false;
        int start = 0, end = 0;
        char ch;
        while (end <= text.length()) {
            ch = 0;
            if (end < text.length()) {
                ch = text.charAt(end);
            }
            if (spaces) {
                if (ch == 0 || ch != ' ') {
                    if (start + 1 == end && this.column > this.bestWidth && split && start != 0
                            && end != text.length()) {
                        writeIndent();
                    } else {
                        int len = end - start;
                        this.column += len;
                        stream.write(text, start, len);
                    }
                    start = end;
                }
            } else if (breaks) {
                if (ch == 0 || CharConstants.LINEBR.hasNo(ch)) {
                    if (text.charAt(start) == '\n') {
                        writeLineBreak(null);
                    }
                    String data = text.substring(start, end);
                    for (char br : data.toCharArray()) {
                        if (br == '\n') {
                            writeLineBreak(null);
                        } else {
                            writeLineBreak(String.valueOf(br));
                        }
                    }
                    writeIndent();
                    start = end;
                }
            } else {
                if (CharConstants.LINEBR.has(ch, "\0 \'")) {
                    if (start < end) {
                        int len = end - start;
                        this.column += len;
                        stream.write(text, start, len);
                        start = end;
                    }
                }
            }
            if (ch == '\'') {
                this.column += 2;
                stream.write("''");
                start = end + 1;
            }
            if (ch != 0) {
                spaces = ch == ' ';
                breaks = CharConstants.LINEBR.has(ch);
            }
            end++;
        }
        writeIndicator("'", false, false, false);
    }

    private void writeDoubleQuoted(String text, boolean split) {
        writeIndicator("\"", true, false, false);
        int start = 0;
        int end = 0;
        while (end <= text.length()) {
            Character ch = null;
            if (end < text.length()) {
                ch = text.charAt(end);
            }
            if (ch == null || "\"\\\u0085\u2028\u2029\uFEFF".indexOf(ch) != -1
                    || !('\u0020' <= ch && ch <= '\u007E')) {
                if (start < end) {
                    int len = end - start;
                    this.column += len;
                    stream.write(text, start, len);
                    start = end;
                }
                if (ch != null) {
                    String data;
                    if (ESCAPE_REPLACEMENTS.containsKey(ch)) {
                        data = "\\" + ESCAPE_REPLACEMENTS.get(ch);
                    } else if (!this.allowUnicode || !StreamReader.isPrintable(ch)) {
                        // if !allowUnicode or the character is not printable,
                        // we must encode it
                        if (ch <= '\u00FF') {
                            String s = "0" + Integer.toString(ch, 16);
                            data = "\\x" + s.substring(s.length() - 2);
                        } else if (ch >= '\uD800' && ch <= '\uDBFF') {
                            if (end + 1 < text.length()) {
                                Character ch2 = text.charAt(++end);
                                String s = "000" + Long.toHexString(Character.toCodePoint(ch, ch2));
                                data = "\\U" + s.substring(s.length() - 8);
                            } else {
                                String s = "000" + Integer.toString(ch, 16);
                                data = "\\u" + s.substring(s.length() - 4);
                            }
                        } else {
                            String s = "000" + Integer.toString(ch, 16);
                            data = "\\u" + s.substring(s.length() - 4);
                        }
                    } else {
                        data = String.valueOf(ch);
                    }
                    this.column += data.length();
                    stream.write(data);
                    start = end + 1;
                }
            }
            if ((0 < end && end < (text.length() - 1)) && (ch == ' ' || start >= end)
                    && (this.column + (end - start)) > this.bestWidth && split) {
                String data;
                if (start >= end) {
                    data = "\\";
                } else {
                    data = text.substring(start, end) + "\\";
                }
                if (start < end) {
                    start = end;
                }
                this.column += data.length();
                stream.write(data);
                writeIndent();
                this.whitespace = false;
                this.indention = false;
                if (text.charAt(start) == ' ') {
                    data = "\\";
                    this.column += data.length();
                    stream.write(data);
                }
            }
            end += 1;
        }
        writeIndicator("\"", false, false, false);
    }

    private String determineBlockHints(String text) {
        StringBuilder hints = new StringBuilder();
        if (CharConstants.LINEBR.has(text.charAt(0), " ")) {
            hints.append(bestIndent);
        }
        char ch1 = text.charAt(text.length() - 1);
        if (CharConstants.LINEBR.hasNo(ch1)) {
            hints.append("-");
        } else if (text.length() == 1 || CharConstants.LINEBR.has(text.charAt(text.length() - 2))) {
            hints.append("+");
        }
        return hints.toString();
    }

    void writeFolded(String text, boolean split) {
        String hints = determineBlockHints(text);
        writeIndicator(">" + hints, true, false, false);
        if (hints.length() > 0 && (hints.charAt(hints.length() - 1) == '+')) {
            openEnded = true;
        }
        writeLineBreak(null);
        boolean leadingSpace = true;
        boolean spaces = false;
        boolean breaks = true;
        int start = 0, end = 0;
        while (end <= text.length()) {
            char ch = 0;
            if (end < text.length()) {
                ch = text.charAt(end);
            }
            if (breaks) {
                if (ch == 0 || CharConstants.LINEBR.hasNo(ch)) {
                    if (!leadingSpace && ch != 0 && ch != ' ' && text.charAt(start) == '\n') {
                        writeLineBreak(null);
                    }
                    leadingSpace = ch == ' ';
                    String data = text.substring(start, end);
                    for (char br : data.toCharArray()) {
                        if (br == '\n') {
                            writeLineBreak(null);
                        } else {
                            writeLineBreak(String.valueOf(br));
                        }
                    }
                    if (ch != 0) {
                        writeIndent();
                    }
                    start = end;
                }
            } else if (spaces) {
                if (ch != ' ') {
                    if (start + 1 == end && this.column > this.bestWidth && split) {
                        writeIndent();
                    } else {
                        int len = end - start;
                        this.column += len;
                        stream.write(text, start, len);
                    }
                    start = end;
                }
            } else {
                if (CharConstants.LINEBR.has(ch, "\0 ")) {
                    int len = end - start;
                    this.column += len;
                    stream.write(text, start, len);
                    if (ch == 0) {
                        writeLineBreak(null);
                    }
                    start = end;
                }
            }
            if (ch != 0) {
                breaks = CharConstants.LINEBR.has(ch);
                spaces = ch == ' ';
            }
            end++;
        }
    }

    void writeLiteral(String text) {
        String hints = determineBlockHints(text);
        writeIndicator("|" + hints, true, false, false);
        if (hints.length() > 0 && (hints.charAt(hints.length() - 1)) == '+') {
            openEnded = true;
        }
        writeLineBreak(null);
        boolean breaks = true;
        int start = 0, end = 0;
        while (end <= text.length()) {
            char ch = 0;
            if (end < text.length()) {
                ch = text.charAt(end);
            }
            if (breaks) {
                if (ch == 0 || CharConstants.LINEBR.hasNo(ch)) {
                    String data = text.substring(start, end);
                    for (char br : data.toCharArray()) {
                        if (br == '\n') {
                            writeLineBreak(null);
                        } else {
                            writeLineBreak(String.valueOf(br));
                        }
                    }
                    if (ch != 0) {
                        writeIndent();
                    }
                    start = end;
                }
            } else {
                if (ch == 0 || CharConstants.LINEBR.has(ch)) {
                    stream.write(text, start, end - start);
                    if (ch == 0) {
                        writeLineBreak(null);
                    }
                    start = end;
                }
            }
            if (ch != 0) {
                breaks = CharConstants.LINEBR.has(ch);
            }
            end++;
        }
    }

    void writePlain(String text, boolean split) {
        if (rootContext) {
            openEnded = true;
        }
        if (text.length() == 0) {
            return;
        }
        if (!this.whitespace) {
            this.column++;
            stream.write(SPACE);
        }
        this.whitespace = false;
        this.indention = false;
        boolean spaces = false;
        boolean breaks = false;
        int start = 0, end = 0;
        while (end <= text.length()) {
            char ch = 0;
            if (end < text.length()) {
                ch = text.charAt(end);
            }
            if (spaces) {
                if (ch != ' ') {
                    if (start + 1 == end && this.column > this.bestWidth && split) {
                        writeIndent();
                        this.whitespace = false;
                        this.indention = false;
                    } else {
                        int len = end - start;
                        this.column += len;
                        stream.write(text, start, len);
                    }
                    start = end;
                }
            } else if (breaks) {
                if (CharConstants.LINEBR.hasNo(ch)) {
                    if (text.charAt(start) == '\n') {
                        writeLineBreak(null);
                    }
                    String data = text.substring(start, end);
                    for (char br : data.toCharArray()) {
                        if (br == '\n') {
                            writeLineBreak(null);
                        } else {
                            writeLineBreak(String.valueOf(br));
                        }
                    }
                    writeIndent();
                    this.whitespace = false;
                    this.indention = false;
                    start = end;
                }
            } else {
                if (CharConstants.LINEBR.has(ch, "\0 ")) {
                    int len = end - start;
                    this.column += len;
                    stream.write(text, start, len);
                    start = end;
                }
            }
            if (ch != 0) {
                spaces = ch == ' ';
                breaks = CharConstants.LINEBR.has(ch);
            }
            end++;
        }
    }
}
