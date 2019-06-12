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

import static org.snakeyaml.engine.v1.common.CharConstants.ESCAPES;
import static org.snakeyaml.engine.v1.common.CharConstants.ESCAPE_CODES;
import static org.snakeyaml.engine.v1.common.CharConstants.ESCAPE_REPLACEMENTS;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.snakeyaml.engine.v1.common.Anchor;
import org.snakeyaml.engine.v1.common.ArrayStack;
import org.snakeyaml.engine.v1.common.CharConstants;
import org.snakeyaml.engine.v1.common.ScalarStyle;
import org.snakeyaml.engine.v1.common.UriEncoder;
import org.snakeyaml.engine.v1.exceptions.Mark;
import org.snakeyaml.engine.v1.exceptions.ScannerException;
import org.snakeyaml.engine.v1.exceptions.YamlEngineException;
import org.snakeyaml.engine.v1.tokens.AliasToken;
import org.snakeyaml.engine.v1.tokens.AnchorToken;
import org.snakeyaml.engine.v1.tokens.BlockEndToken;
import org.snakeyaml.engine.v1.tokens.BlockEntryToken;
import org.snakeyaml.engine.v1.tokens.BlockMappingStartToken;
import org.snakeyaml.engine.v1.tokens.BlockSequenceStartToken;
import org.snakeyaml.engine.v1.tokens.DirectiveToken;
import org.snakeyaml.engine.v1.tokens.DocumentEndToken;
import org.snakeyaml.engine.v1.tokens.DocumentStartToken;
import org.snakeyaml.engine.v1.tokens.FlowEntryToken;
import org.snakeyaml.engine.v1.tokens.FlowMappingEndToken;
import org.snakeyaml.engine.v1.tokens.FlowMappingStartToken;
import org.snakeyaml.engine.v1.tokens.FlowSequenceEndToken;
import org.snakeyaml.engine.v1.tokens.FlowSequenceStartToken;
import org.snakeyaml.engine.v1.tokens.KeyToken;
import org.snakeyaml.engine.v1.tokens.ScalarToken;
import org.snakeyaml.engine.v1.tokens.StreamEndToken;
import org.snakeyaml.engine.v1.tokens.StreamStartToken;
import org.snakeyaml.engine.v1.tokens.TagToken;
import org.snakeyaml.engine.v1.tokens.TagTuple;
import org.snakeyaml.engine.v1.tokens.Token;
import org.snakeyaml.engine.v1.tokens.ValueToken;

/**
 * <pre>
 * Scanner produces tokens of the following types:
 * STREAM-START
 * STREAM-END
 * DIRECTIVE(name, value)
 * DOCUMENT-START
 * DOCUMENT-END
 * BLOCK-SEQUENCE-START
 * BLOCK-MAPPING-START
 * BLOCK-END
 * FLOW-SEQUENCE-START
 * FLOW-MAPPING-START
 * FLOW-SEQUENCE-END
 * FLOW-MAPPING-END
 * BLOCK-ENTRY
 * FLOW-ENTRY
 * KEY
 * VALUE
 * ALIAS(value)
 * ANCHOR(value)
 * TAG(value)
 * SCALAR(value, plain, style)
 * Read comments in the Scanner code for more details.
 * </pre>
 */
public final class ScannerImpl implements Scanner {
    /**
     * A regular expression matching characters which are not in the hexadecimal
     * set (0-9, A-F, a-f).
     */
    private final static Pattern NOT_HEXA = Pattern.compile("[^0-9A-Fa-f]");

    private final StreamReader reader;
    // Had we reached the end of the stream?
    private boolean done = false;

    // The number of unclosed '{' and '['. `flow_level == 0` means block
    // context.
    private int flowLevel = 0;

    // List of processed tokens that are not yet emitted.
    private List<Token> tokens;

    // Number of tokens that were emitted through the `get_token` method.
    private int tokensTaken = 0;

    // The current indentation level.
    private int indent = -1;

    // Past indentation levels.
    private ArrayStack<Integer> indents;

    // Variables related to simple keys treatment.

    /**
     * <pre>
     * A simple key is a key that is not denoted by the '?' indicator.
     * Example of simple keys:
     *   ---
     *   block simple key: value
     *   ? not a simple key:
     *   : { flow simple key: value }
     * We emit the KEY token before all keys, so when we find a potential
     * simple key, we try to locate the corresponding ':' indicator.
     * Simple keys should be limited to a single line and 1024 characters.
     *
     * Can a simple key start at the current position? A simple key may
     * start:
     * - at the beginning of the line, not counting indentation spaces
     *       (in block context),
     * - after '{', '[', ',' (in the flow context),
     * - after '?', ':', '-' (in the block context).
     * In the block context, this flag also signifies if a block collection
     * may start at the current position.
     * </pre>
     */
    private boolean allowSimpleKey = true;

    /*
     * Keep track of possible simple keys. This is a dictionary. The key is
     * `flow_level`; there can be no more that one possible simple key for each
     * level. The value is a SimpleKey record: (token_number, required, index,
     * line, column, mark) A simple key may start with ALIAS, ANCHOR, TAG,
     * SCALAR(flow), '[', or '{' tokens.
     */
    private Map<Integer, SimpleKey> possibleSimpleKeys;

    public ScannerImpl(StreamReader reader) {
        this.reader = reader;
        this.tokens = new ArrayList<Token>(100);
        this.indents = new ArrayStack<Integer>(10);
        // The order in possibleSimpleKeys is kept for nextPossibleSimpleKey()
        this.possibleSimpleKeys = new LinkedHashMap<Integer, SimpleKey>();
        fetchStreamStart();// Add the STREAM-START token.
    }

    /**
     * Check whether the next token is one of the given types.
     */
    public boolean checkToken(Token.ID... choices) {
        while (needMoreTokens()) {
            fetchMoreTokens();
        }
        if (!this.tokens.isEmpty()) {
            if (choices.length == 0) {
                return true;
            }
            // since profiler puts this method on top (it is used a lot), we
            // should not use 'foreach' here because of the performance reasons
            Token firstToken = this.tokens.get(0);
            Token.ID first = firstToken.getTokenId();
            for (int i = 0; i < choices.length; i++) {
                if (first == choices[i]) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Return the next token, but do not delete it from the queue.
     */
    public Token peekToken() {
        while (needMoreTokens()) {
            fetchMoreTokens();
        }
        return this.tokens.get(0);
    }

    @Override
    public boolean hasNext() {
        return checkToken();
    }

    /**
     * Return the next token, removing it from the queue.
     */
    public Token next() {
        this.tokensTaken++;
        return this.tokens.remove(0);
    }

    // Private methods.

    /**
     * Returns true if more tokens should be scanned.
     */
    private boolean needMoreTokens() {
        // If we are done, we do not require more tokens.
        if (this.done) {
            return false;
        }
        // If we aren't done, but we have no tokens, we need to scan more.
        if (this.tokens.isEmpty()) {
            return true;
        }
        // The current token may be a potential simple key, so we
        // need to look further.
        stalePossibleSimpleKeys();
        return nextPossibleSimpleKey() == this.tokensTaken;
    }

    /**
     * Fetch one or more tokens from the StreamReader.
     */
    private void fetchMoreTokens() {
        // Eat whitespaces and comments until we reach the next token.
        scanToNextToken();
        // Remove obsolete possible simple keys.
        stalePossibleSimpleKeys();
        // Compare the current indentation and column. It may add some tokens
        // and decrease the current indentation level.
        unwindIndent(reader.getColumn());
        // Peek the next code point, to decide what the next group of tokens
        // will look like.
        int c = reader.peek();
        switch (c) {
            case '\0':
                // Is it the end of stream?
                fetchStreamEnd();
                return;
            case '%':
                // Is it a directive?
                if (checkDirective()) {
                    fetchDirective();
                    return;
                }
                break;
            case '-':
                // Is it the document start?
                if (checkDocumentStart()) {
                    fetchDocumentStart();
                    return;
                    // Is it the block entry indicator?
                } else if (checkBlockEntry()) {
                    fetchBlockEntry();
                    return;
                }
                break;
            case '.':
                // Is it the document end?
                if (checkDocumentEnd()) {
                    fetchDocumentEnd();
                    return;
                }
                break;
            // TODO support for BOM within a stream.
            case '[':
                // Is it the flow sequence start indicator?
                fetchFlowSequenceStart();
                return;
            case '{':
                // Is it the flow mapping start indicator?
                fetchFlowMappingStart();
                return;
            case ']':
                // Is it the flow sequence end indicator?
                fetchFlowSequenceEnd();
                return;
            case '}':
                // Is it the flow mapping end indicator?
                fetchFlowMappingEnd();
                return;
            case ',':
                // Is it the flow entry indicator?
                fetchFlowEntry();
                return;
            // see block entry indicator above
            case '?':
                // Is it the key indicator?
                if (checkKey()) {
                    fetchKey();
                    return;
                }
                break;
            case ':':
                // Is it the value indicator?
                if (checkValue()) {
                    fetchValue();
                    return;
                }
                break;
            case '*':
                // Is it an alias?
                fetchAlias();
                return;
            case '&':
                // Is it an anchor?
                fetchAnchor();
                return;
            case '!':
                // Is it a tag?
                fetchTag();
                return;
            case '|':
                // Is it a literal scalar?
                if (this.flowLevel == 0) {
                    fetchLiteral();
                    return;
                }
                break;
            case '>':
                // Is it a folded scalar?
                if (this.flowLevel == 0) {
                    fetchFolded();
                    return;
                }
                break;
            case '\'':
                // Is it a single quoted scalar?
                fetchSingle();
                return;
            case '"':
                // Is it a double quoted scalar?
                fetchDouble();
                return;
        }
        // It must be a plain scalar then.
        if (checkPlain()) {
            fetchPlain();
            return;
        }
        // No? It's an error. Let's produce a nice error message. We do this by
        // converting escaped characters into their escape sequences. This is a
        // backwards use of the ESCAPE_REPLACEMENTS map.
        String chRepresentation = String.valueOf(Character.toChars(c));
        if (ESCAPES.containsKey(Character.valueOf((char) c))) {
            chRepresentation = "\\" + ESCAPES.get(Character.valueOf((char) c));
        }
        if (c == '\t')
            chRepresentation += "(TAB)";
        String text = String
                .format("found character '%s' that cannot start any token. (Do not use %s for indentation)",
                        chRepresentation, chRepresentation);
        throw new ScannerException("while scanning for the next token", Optional.empty(), text, reader.getMark());
    }

    // Simple keys treatment.

    /**
     * Return the number of the nearest possible simple key. Actually we don't
     * need to loop through the whole dictionary.
     */
    private int nextPossibleSimpleKey() {
        /*
         * Because this.possibleSimpleKeys is ordered we can simply take the first key
         */
        if (!this.possibleSimpleKeys.isEmpty()) {
            return this.possibleSimpleKeys.values().iterator().next().getTokenNumber();
        }
        return -1;
    }

    /**
     * <pre>
     * Remove entries that are no longer possible simple keys. According to
     * the YAML specification, simple keys
     * - should be limited to a single line,
     * - should be no longer than 1024 characters.
     * Disabling this procedure will allow simple keys of any length and
     * height (may cause problems if indentation is broken though).
     * </pre>
     */
    private void stalePossibleSimpleKeys() {
        if (!this.possibleSimpleKeys.isEmpty()) {
            for (Iterator<SimpleKey> iterator = this.possibleSimpleKeys.values().iterator(); iterator
                    .hasNext(); ) {
                SimpleKey key = iterator.next();
                if ((key.getLine() != reader.getLine())
                        || (reader.getIndex() - key.getIndex() > 1024)) {
                    // If the key is not on the same line as the current
                    // position OR the difference in column between the token
                    // start and the current position is more than the maximum
                    // simple key length, then this cannot be a simple key.
                    if (key.isRequired()) {
                        // If the key was required, this implies an error
                        // condition.
                        throw new ScannerException("while scanning a simple key", key.getMark(),
                                "could not find expected ':'", reader.getMark());
                    }
                    iterator.remove();
                }
            }
        }
    }

    /**
     * The next token may start a simple key. We check if it's possible and save
     * its position. This function is called for ALIAS, ANCHOR, TAG,
     * SCALAR(flow), '[', and '{'.
     */
    private void savePossibleSimpleKey() {
        // The next token may start a simple key. We check if it's possible
        // and save its position. This function is called for
        // ALIAS, ANCHOR, TAG, SCALAR(flow), '[', and '{'.

        // Check if a simple key is required at the current position.
        // A simple key is required if this position is the root flowLevel, AND
        // the current indentation level is the same as the last indent-level.
        boolean required = (this.flowLevel == 0) && (this.indent == this.reader.getColumn());

        if (allowSimpleKey || !required) {
            // A simple key is required only if it is the first token in the
            // current line. Therefore it is always allowed.
        } else {
            throw new YamlEngineException(
                    "A simple key is required only if it is the first token in the current line");
        }

        // The next token might be a simple key. Let's save it's number and
        // position.
        if (this.allowSimpleKey) {
            removePossibleSimpleKey();
            int tokenNumber = this.tokensTaken + this.tokens.size();
            SimpleKey key = new SimpleKey(tokenNumber, required, reader.getIndex(),
                    reader.getLine(), this.reader.getColumn(), this.reader.getMark());
            this.possibleSimpleKeys.put(this.flowLevel, key);
        }
    }

    /**
     * Remove the saved possible key position at the current flow level.
     */
    private void removePossibleSimpleKey() {
        SimpleKey key = possibleSimpleKeys.remove(flowLevel);
        if (key != null && key.isRequired()) {
            throw new ScannerException("while scanning a simple key", key.getMark(),
                    "could not find expected ':'", reader.getMark());
        }
    }

    // Indentation functions.

    /**
     * * Handle implicitly ending multiple levels of block nodes by decreased
     * indentation. This function becomes important on lines 4 and 7 of this
     * example:
     *
     * <pre>
     * 1) book one:
     * 2)   part one:
     * 3)     chapter one
     * 4)   part two:
     * 5)     chapter one
     * 6)     chapter two
     * 7) book two:
     * </pre>
     * <p>
     * In flow context, tokens should respect indentation. Actually the
     * condition should be `self.indent &gt;= column` according to the spec. But
     * this condition will prohibit intuitively correct constructions such as
     * key : { } </pre>
     */
    private void unwindIndent(int col) {
        // In the flow context, indentation is ignored. We make the scanner less
        // restrictive then specification requires.
        if (this.flowLevel != 0) {
            return;
        }

        // In block context, we may need to issue the BLOCK-END tokens.
        while (this.indent > col) {
            Optional<Mark> mark = reader.getMark();
            this.indent = this.indents.pop();
            this.tokens.add(new BlockEndToken(mark, mark));
        }
    }

    /**
     * Check if we need to increase indentation.
     */
    private boolean addIndent(int column) {
        if (this.indent < column) {
            this.indents.push(this.indent);
            this.indent = column;
            return true;
        }
        return false;
    }

    // Fetchers.

    /**
     * We always add STREAM-START as the first token and STREAM-END as the last
     * token.
     */
    private void fetchStreamStart() {
        // Read the token.
        Optional<Mark> mark = reader.getMark();

        // Add STREAM-START.
        Token token = new StreamStartToken(mark, mark);
        this.tokens.add(token);
    }

    private void fetchStreamEnd() {
        // Set the current intendation to -1.
        unwindIndent(-1);

        // Reset simple keys.
        removePossibleSimpleKey();
        this.allowSimpleKey = false;
        this.possibleSimpleKeys.clear();

        // Read the token.
        Optional<Mark> mark = reader.getMark();

        // Add STREAM-END.
        Token token = new StreamEndToken(mark, mark);
        this.tokens.add(token);

        // The stream is finished.
        this.done = true;
    }

    /**
     * Fetch a YAML directive. Directives are presentation details that are
     * interpreted as instructions to the processor. YAML defines two kinds of
     * directives, YAML and TAG; all other types are reserved for future use.
     */
    private void fetchDirective() {
        // Set the current intendation to -1.
        unwindIndent(-1);

        // Reset simple keys.
        removePossibleSimpleKey();
        this.allowSimpleKey = false;

        // Scan and add DIRECTIVE.
        Token tok = scanDirective();
        this.tokens.add(tok);
    }

    /**
     * Fetch a document-start token ("---").
     */
    private void fetchDocumentStart() {
        fetchDocumentIndicator(true);
    }

    /**
     * Fetch a document-end token ("...").
     */
    private void fetchDocumentEnd() {
        fetchDocumentIndicator(false);
    }

    /**
     * Fetch a document indicator, either "---" for "document-start", or else
     * "..." for "document-end. The type is chosen by the given boolean.
     */
    private void fetchDocumentIndicator(boolean isDocumentStart) {
        // Set the current intendation to -1.
        unwindIndent(-1);

        // Reset simple keys. Note that there could not be a block collection
        // after '---'.
        removePossibleSimpleKey();
        this.allowSimpleKey = false;

        // Add DOCUMENT-START or DOCUMENT-END.
        Optional<Mark> startMark = reader.getMark();
        reader.forward(3);
        Optional<Mark> endMark = reader.getMark();
        Token token;
        if (isDocumentStart) {
            token = new DocumentStartToken(startMark, endMark);
        } else {
            token = new DocumentEndToken(startMark, endMark);
        }
        this.tokens.add(token);
    }

    private void fetchFlowSequenceStart() {
        fetchFlowCollectionStart(false);
    }

    private void fetchFlowMappingStart() {
        fetchFlowCollectionStart(true);
    }

    /**
     * Fetch a flow-style collection start, which is either a sequence or a
     * mapping. The type is determined by the given boolean.
     * <p>
     * A flow-style collection is in a format similar to JSON. Sequences are
     * started by '[' and ended by ']'; mappings are started by '{' and ended by
     * '}'.
     *
     * @param isMappingStart
     */
    private void fetchFlowCollectionStart(boolean isMappingStart) {
        // '[' and '{' may start a simple key.
        savePossibleSimpleKey();

        // Increase the flow level.
        this.flowLevel++;

        // Simple keys are allowed after '[' and '{'.
        this.allowSimpleKey = true;

        // Add FLOW-SEQUENCE-START or FLOW-MAPPING-START.
        Optional<Mark> startMark = reader.getMark();
        reader.forward(1);
        Optional<Mark> endMark = reader.getMark();
        Token token;
        if (isMappingStart) {
            token = new FlowMappingStartToken(startMark, endMark);
        } else {
            token = new FlowSequenceStartToken(startMark, endMark);
        }
        this.tokens.add(token);
    }

    private void fetchFlowSequenceEnd() {
        fetchFlowCollectionEnd(false);
    }

    private void fetchFlowMappingEnd() {
        fetchFlowCollectionEnd(true);
    }

    /**
     * Fetch a flow-style collection end, which is either a sequence or a
     * mapping. The type is determined by the given boolean.
     * <p>
     * A flow-style collection is in a format similar to JSON. Sequences are
     * started by '[' and ended by ']'; mappings are started by '{' and ended by
     * '}'.
     */
    private void fetchFlowCollectionEnd(boolean isMappingEnd) {
        // Reset possible simple key on the current level.
        removePossibleSimpleKey();

        // Decrease the flow level.
        this.flowLevel--;

        // No simple keys after ']' or '}'.
        this.allowSimpleKey = false;

        // Add FLOW-SEQUENCE-END or FLOW-MAPPING-END.
        Optional<Mark> startMark = reader.getMark();
        reader.forward();
        Optional<Mark> endMark = reader.getMark();
        Token token;
        if (isMappingEnd) {
            token = new FlowMappingEndToken(startMark, endMark);
        } else {
            token = new FlowSequenceEndToken(startMark, endMark);
        }
        this.tokens.add(token);
    }

    /**
     * Fetch an entry in the flow style. Flow-style entries occur either
     * immediately after the start of a collection, or else after a comma.
     */
    private void fetchFlowEntry() {
        // Simple keys are allowed after ','.
        this.allowSimpleKey = true;

        // Reset possible simple key on the current level.
        removePossibleSimpleKey();

        // Add FLOW-ENTRY.
        Optional<Mark> startMark = reader.getMark();
        reader.forward();
        Optional<Mark> endMark = reader.getMark();
        Token token = new FlowEntryToken(startMark, endMark);
        this.tokens.add(token);
    }

    /**
     * Fetch an entry in the block style.
     */
    private void fetchBlockEntry() {
        // Block context needs additional checks.
        if (this.flowLevel == 0) {
            // Are we allowed to start a new entry?
            if (!this.allowSimpleKey) {
                throw new ScannerException("", Optional.empty(), "sequence entries are not allowed here",
                        reader.getMark());
            }

            // We may need to add BLOCK-SEQUENCE-START.
            if (addIndent(this.reader.getColumn())) {
                Optional<Mark> mark = reader.getMark();
                this.tokens.add(new BlockSequenceStartToken(mark, mark));
            }
        } else {
            // It's an error for the block entry to occur in the flow
            // context,but we let the scanner detect this.
        }
        // Simple keys are allowed after '-'.
        this.allowSimpleKey = true;

        // Reset possible simple key on the current level.
        removePossibleSimpleKey();

        // Add BLOCK-ENTRY.
        Optional<Mark> startMark = reader.getMark();
        reader.forward();
        Optional<Mark> endMark = reader.getMark();
        Token token = new BlockEntryToken(startMark, endMark);
        this.tokens.add(token);
    }

    /**
     * Fetch a key in a block-style mapping.
     */
    private void fetchKey() {
        // Block context needs additional checks.
        if (this.flowLevel == 0) {
            // Are we allowed to start a key (not necessary a simple)?
            if (!this.allowSimpleKey) {
                throw new ScannerException("mapping keys are not allowed here",
                        reader.getMark());
            }
            // We may need to add BLOCK-MAPPING-START.
            if (addIndent(this.reader.getColumn())) {
                Optional<Mark> mark = reader.getMark();
                this.tokens.add(new BlockMappingStartToken(mark, mark));
            }
        }
        // Simple keys are allowed after '?' in the block context.
        this.allowSimpleKey = this.flowLevel == 0;

        // Reset possible simple key on the current level.
        removePossibleSimpleKey();

        // Add KEY.
        Optional<Mark> startMark = reader.getMark();
        reader.forward();
        Optional<Mark> endMark = reader.getMark();
        Token token = new KeyToken(startMark, endMark);
        this.tokens.add(token);
    }

    /**
     * Fetch a value in a block-style mapping.
     */
    private void fetchValue() {
        // Do we determine a simple key?
        SimpleKey key = this.possibleSimpleKeys.remove(this.flowLevel);
        if (key != null) {
            // Add KEY.
            this.tokens.add(key.getTokenNumber() - this.tokensTaken, new KeyToken(key.getMark(),
                    key.getMark()));

            // If this key starts a new block mapping, we need to add
            // BLOCK-MAPPING-START.
            if (this.flowLevel == 0) {
                if (addIndent(key.getColumn())) {
                    this.tokens.add(key.getTokenNumber() - this.tokensTaken,
                            new BlockMappingStartToken(key.getMark(), key.getMark()));
                }
            }
            // There cannot be two simple keys one after another.
            this.allowSimpleKey = false;

        } else {
            // It must be a part of a complex key.
            // Block context needs additional checks. Do we really need them?
            // They will be caught by the scanner anyway.
            if (this.flowLevel == 0) {

                // We are allowed to start a complex value if and only if we can
                // start a simple key.
                if (!this.allowSimpleKey) {
                    throw new ScannerException("mapping values are not allowed here", reader.getMark());
                }
            }

            // If this value starts a new block mapping, we need to add
            // BLOCK-MAPPING-START. It will be detected as an error later by
            // the scanner.
            if (flowLevel == 0) {
                if (addIndent(reader.getColumn())) {
                    Optional<Mark> mark = reader.getMark();
                    this.tokens.add(new BlockMappingStartToken(mark, mark));
                }
            }

            // Simple keys are allowed after ':' in the block context.
            allowSimpleKey = flowLevel == 0;

            // Reset possible simple key on the current level.
            removePossibleSimpleKey();
        }
        // Add VALUE.
        Optional<Mark> startMark = reader.getMark();
        reader.forward();
        Optional<Mark> endMark = reader.getMark();
        Token token = new ValueToken(startMark, endMark);
        this.tokens.add(token);
    }

    /**
     * Fetch an alias, which is a reference to an anchor. Aliases take the
     * format:
     *
     * <pre>
     * *(anchor name)
     * </pre>
     */
    private void fetchAlias() {
        // ALIAS could be a simple key.
        savePossibleSimpleKey();

        // No simple keys after ALIAS.
        this.allowSimpleKey = false;

        // Scan and add ALIAS.
        Token tok = scanAnchor(false);
        this.tokens.add(tok);
    }

    /**
     * Fetch an anchor. Anchors take the form:
     *
     * <pre>
     * &(anchor name)
     * </pre>
     */
    private void fetchAnchor() {
        // ANCHOR could start a simple key.
        savePossibleSimpleKey();

        // No simple keys after ANCHOR.
        this.allowSimpleKey = false;

        // Scan and add ANCHOR.
        Token tok = scanAnchor(true);
        this.tokens.add(tok);
    }

    /**
     * Fetch a tag. Tags take a complex form.
     */
    private void fetchTag() {
        // TAG could start a simple key.
        savePossibleSimpleKey();

        // No simple keys after TAG.
        this.allowSimpleKey = false;

        // Scan and add TAG.
        Token tok = scanTag();
        this.tokens.add(tok);
    }

    /**
     * Fetch a literal scalar, denoted with a vertical-bar. This is the type
     * best used for source code and other content, such as binary data, which
     * must be included verbatim.
     */
    private void fetchLiteral() {
        fetchBlockScalar(ScalarStyle.LITERAL);
    }

    /**
     * Fetch a folded scalar, denoted with a greater-than sign. This is the type
     * best used for long content, such as the text of a chapter or description.
     */
    private void fetchFolded() {
        fetchBlockScalar(ScalarStyle.FOLDED);
    }

    /**
     * Fetch a block scalar (literal or folded).
     *
     * @param style
     */
    private void fetchBlockScalar(ScalarStyle style) {
        // A simple key may follow a block scalar.
        this.allowSimpleKey = true;

        // Reset possible simple key on the current level.
        removePossibleSimpleKey();

        // Scan and add SCALAR.
        Token tok = scanBlockScalar(style);
        this.tokens.add(tok);
    }

    /**
     * Fetch a single-quoted (') scalar.
     */
    private void fetchSingle() {
        fetchFlowScalar(ScalarStyle.SINGLE_QUOTED);
    }

    /**
     * Fetch a double-quoted (") scalar.
     */
    private void fetchDouble() {
        fetchFlowScalar(ScalarStyle.DOUBLE_QUOTED);
    }

    /**
     * Fetch a flow scalar (single- or double-quoted).
     *
     * @param style
     */
    private void fetchFlowScalar(ScalarStyle style) {
        // A flow scalar could be a simple key.
        savePossibleSimpleKey();

        // No simple keys after flow scalars.
        this.allowSimpleKey = false;

        // Scan and add SCALAR.
        Token tok = scanFlowScalar(style);
        this.tokens.add(tok);
    }

    /**
     * Fetch a plain scalar.
     */
    private void fetchPlain() {
        // A plain scalar could be a simple key.
        savePossibleSimpleKey();

        // No simple keys after plain scalars. But note that `scan_plain` will
        // change this flag if the scan is finished at the beginning of the
        // line.
        this.allowSimpleKey = false;

        // Scan and add SCALAR. May change `allow_simple_key`.
        Token tok = scanPlain();
        this.tokens.add(tok);
    }

    // Checkers.

    /**
     * Returns true if the next thing on the reader is a directive, given that
     * the leading '%' has already been checked.
     */
    private boolean checkDirective() {
        // DIRECTIVE: ^ '%' ...
        // The '%' indicator is already checked.
        return reader.getColumn() == 0;
    }

    /**
     * Returns true if the next thing on the reader is a document-start ("---").
     * A document-start is always followed immediately by a new line.
     */
    private boolean checkDocumentStart() {
        // DOCUMENT-START: ^ '---' (' '|'\n')
        if (reader.getColumn() == 0) {
            if ("---".equals(reader.prefix(3)) && CharConstants.NULL_BL_T_LINEBR.has(reader.peek(3))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the next thing on the reader is a document-end ("..."). A
     * document-end is always followed immediately by a new line.
     */
    private boolean checkDocumentEnd() {
        // DOCUMENT-END: ^ '...' (' '|'\n')
        if (reader.getColumn() == 0) {
            if ("...".equals(reader.prefix(3)) && CharConstants.NULL_BL_T_LINEBR.has(reader.peek(3))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the next thing on the reader is a block token.
     */
    private boolean checkBlockEntry() {
        // BLOCK-ENTRY: '-' (' '|'\n')
        return CharConstants.NULL_BL_T_LINEBR.has(reader.peek(1));
    }

    /**
     * Returns true if the next thing on the reader is a key token.
     */
    private boolean checkKey() {
        // KEY(flow context): '?'
        if (this.flowLevel != 0) {
            return true;
        } else {
            // KEY(block context): '?' (' '|'\n')
            return CharConstants.NULL_BL_T_LINEBR.has(reader.peek(1));
        }
    }

    /**
     * Returns true if the next thing on the reader is a value token.
     */
    private boolean checkValue() {
        // VALUE(flow context): ':'
        if (flowLevel != 0) {
            return true;
        } else {
            // VALUE(block context): ':' (' '|'\n')
            return CharConstants.NULL_BL_T_LINEBR.has(reader.peek(1));
        }
    }

    /**
     * Returns true if the next thing on the reader is a plain token.
     */
    private boolean checkPlain() {
        /**
         * <pre>
         * A plain scalar may start with any non-space character except:
         *   '-', '?', ':', ',', '[', ']', '{', '}',
         *   '#', '&amp;', '*', '!', '|', '&gt;', '\'', '\&quot;',
         *   '%', '@', '`'.
         *
         * It may also start with
         *   '-', '?', ':'
         * if it is followed by a non-space character.
         *
         * Note that we limit the last rule to the block context (except the
         * '-' character) because we want the flow context to be space
         * independent.
         * </pre>
         */
        int c = reader.peek();
        // If the next char is NOT one of the forbidden chars above or
        // whitespace, then this is the start of a plain scalar.
        return CharConstants.NULL_BL_T_LINEBR.hasNo(c, "-?:,[]{}#&*!|>\'\"%@`")
                || (CharConstants.NULL_BL_T_LINEBR.hasNo(reader.peek(1)) && (c == '-' || (this.flowLevel == 0 && "?:"
                .indexOf(c) != -1)));
    }

    // Scanners.

    /**
     * <pre>
     * We ignore spaces, line breaks and comments.
     * If we find a line break in the block context, we set the flag
     * `allow_simple_key` on.
     * The byte order mark is stripped if it's the first character in the
     * stream. We do not yet support BOM inside the stream as the
     * specification requires. Any such mark will be considered as a part
     * of the document.
     * TODO: We need to make tab handling rules more sane. A good rule is
     *   Tabs cannot precede tokens
     *   BLOCK-SEQUENCE-START, BLOCK-MAPPING-START, BLOCK-END,
     *   KEY(block), VALUE(block), BLOCK-ENTRY
     * So the checking code is
     *   if &lt;TAB&gt;:
     *       self.allow_simple_keys = False
     * We also need to add the check for `allow_simple_keys == True` to
     * `unwind_indent` before issuing BLOCK-END.
     * Scanners for block, flow, and plain scalars need to be modified.
     * </pre>
     */
    private void scanToNextToken() {
        // If there is a byte order mark (BOM) at the beginning of the stream,
        // forward past it.
        if (reader.getIndex() == 0 && reader.peek() == 0xFEFF) {
            reader.forward();
        }
        boolean found = false;
        while (!found) {
            int ff = 0;
            // Peek ahead until we find the first non-space character, then
            // move forward directly to that character.
            // (allow TAB to precede a token, test J3BT)
            while (reader.peek(ff) == ' ' || reader.peek(ff) == '\t') {
                ff++;
            }
            if (ff > 0) {
                reader.forward(ff);
            }
            // If the character we have skipped forward to is a comment (#),
            // then peek ahead until we find the next end of line. YAML
            // comments are from a # to the next new-line. We then forward
            // past the comment.
            if (reader.peek() == '#') {
                ff = 0;
                while (CharConstants.NULL_OR_LINEBR.hasNo(reader.peek(ff))) {
                    ff++;
                }
                if (ff > 0) {
                    reader.forward(ff);
                }
            }
            // If we scanned a line break, then (depending on flow level),
            // simple keys may be allowed.
            if (scanLineBreak().length() != 0) {// found a line-break
                if (this.flowLevel == 0) {
                    // Simple keys are allowed at flow-level 0 after a line
                    // break
                    this.allowSimpleKey = true;
                }
            } else {
                found = true;
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Token scanDirective() {
        // See the specification for details.
        Optional<Mark> startMark = reader.getMark();
        Optional<Mark> endMark;
        reader.forward();
        String name = scanDirectiveName(startMark);
        Optional<List<?>> value;
        if (DirectiveToken.YAML_DIRECTIVE.equals(name)) {
            value = Optional.of(scanYamlDirectiveValue(startMark));
            endMark = reader.getMark();
        } else if (DirectiveToken.TAG_DIRECTIVE.equals(name)) {
            value = Optional.of(scanTagDirectiveValue(startMark));
            endMark = reader.getMark();
        } else {
            endMark = reader.getMark();
            int ff = 0;
            while (CharConstants.NULL_OR_LINEBR.hasNo(reader.peek(ff))) {
                ff++;
            }
            if (ff > 0) {
                reader.forward(ff);
            }
            value = Optional.empty();
        }
        scanDirectiveIgnoredLine(startMark);
        return new DirectiveToken(name, value, startMark, endMark);
    }

    /**
     * Scan a directive name. Directive names are a series of non-space
     * characters.
     */
    private String scanDirectiveName(Optional<Mark> startMark) {
        // See the specification for details.
        int length = 0;
        // A Directive-name is a sequence of alphanumeric characters
        // (a-z,A-Z,0-9). We scan until we find something that isn't.
        // FIXME this disagrees with the specification.
        int c = reader.peek(length);
        while (CharConstants.ALPHA.has(c)) {
            length++;
            c = reader.peek(length);
        }
        // If the name would be empty, an error occurs.
        if (length == 0) {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark,
                    "expected alphabetic or numeric character, but found " + s + "(" + c
                            + ")", reader.getMark());
        }
        String value = reader.prefixForward(length);
        c = reader.peek();
        if (CharConstants.NULL_BL_LINEBR.hasNo(c)) {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark,
                    "expected alphabetic or numeric character, but found " + s + "(" + c
                            + ")", reader.getMark());
        }
        return value;
    }

    private List<Integer> scanYamlDirectiveValue(Optional<Mark> startMark) {
        // See the specification for details.
        while (reader.peek() == ' ') {
            reader.forward();
        }
        Integer major = scanYamlDirectiveNumber(startMark);
        int c = reader.peek();
        if (c != '.') {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark,
                    "expected a digit or '.', but found " + s + "("
                            + c + ")", reader.getMark());
        }
        reader.forward();
        Integer minor = scanYamlDirectiveNumber(startMark);
        c = reader.peek();
        if (CharConstants.NULL_BL_LINEBR.hasNo(c)) {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark,
                    "expected a digit or ' ', but found " + s + "("
                            + c + ")", reader.getMark());
        }
        List<Integer> result = new ArrayList<Integer>(2);
        result.add(major);
        result.add(minor);
        return result;
    }

    /**
     * Read a %YAML directive number: this is either the major or the minor
     * part. Stop reading at a non-digit character (usually either '.' or '\n').
     */
    private Integer scanYamlDirectiveNumber(Optional<Mark> startMark) {
        // See the specification for details.
        int c = reader.peek();
        if (!Character.isDigit(c)) {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark,
                    "expected a digit, but found " + s + "(" + (c) + ")", reader.getMark());
        }
        int length = 0;
        while (Character.isDigit(reader.peek(length))) {
            length++;
        }
        Integer value = Integer.parseInt(reader.prefixForward(length));
        return value;
    }

    /**
     * <p>
     * Read a %TAG directive value:
     * <p>
     * <pre>
     * s-ignored-space+ c-tag-handle s-ignored-space+ ns-tag-prefix s-l-comments
     * </pre>
     * <p>
     * </p>
     */
    private List<String> scanTagDirectiveValue(Optional<Mark> startMark) {
        // See the specification for details.
        while (reader.peek() == ' ') {
            reader.forward();
        }
        String handle = scanTagDirectiveHandle(startMark);
        while (reader.peek() == ' ') {
            reader.forward();
        }
        String prefix = scanTagDirectivePrefix(startMark);
        List<String> result = new ArrayList<String>(2);
        result.add(handle);
        result.add(prefix);
        return result;
    }

    /**
     * Scan a %TAG directive's handle. This is YAML's c-tag-handle.
     *
     * @param startMark
     * @return the directive value
     */
    private String scanTagDirectiveHandle(Optional<Mark> startMark) {
        // See the specification for details.
        String value = scanTagHandle("directive", startMark);
        int c = reader.peek();
        if (c != ' ') {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark,
                    "expected ' ', but found " + s + "(" + c + ")", reader.getMark());
        }
        return value;
    }

    /**
     * Scan a %TAG directive's prefix. This is YAML's ns-tag-prefix.
     */
    private String scanTagDirectivePrefix(Optional<Mark> startMark) {
        // See the specification for details.
        String value = scanTagUri("directive", startMark);
        int c = reader.peek();
        if (CharConstants.NULL_BL_LINEBR.hasNo(c)) {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark,
                    "expected ' ', but found " + s + "(" + c + ")",
                    reader.getMark());
        }
        return value;
    }

    private void scanDirectiveIgnoredLine(Optional<Mark> startMark) {
        // See the specification for details.
        while (reader.peek() == ' ') {
            reader.forward();
        }
        if (reader.peek() == '#') {
            while (CharConstants.NULL_OR_LINEBR.hasNo(reader.peek())) {
                reader.forward();
            }
        }
        int c = reader.peek();
        String lineBreak = scanLineBreak();
        if (lineBreak.length() == 0 && c != '\0') {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark,
                    "expected a comment or a line break, but found " + s + "(" + c + ")",
                    reader.getMark());
        }
    }

    /**
     * <pre>
     * The specification does not restrict characters for anchors and
     * aliases. This may lead to problems, for instance, the document:
     *   [ *alias, value ]
     * can be interpreted in two ways, as
     *   [ &quot;value&quot; ]
     * and
     *   [ *alias , &quot;value&quot; ]
     * Therefore we restrict aliases to numbers and ASCII letters.
     * </pre>
     */
    private Token scanAnchor(boolean isAnchor) {
        Optional<Mark> startMark = reader.getMark();
        int indicator = reader.peek();
        String name = indicator == '*' ? "alias" : "anchor";
        reader.forward();
        int length = 0;
        int c = reader.peek(length);
        while (CharConstants.ALPHA.has(c)) {
            length++;
            c = reader.peek(length);
        }
        if (length == 0) {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning an " + name, startMark,
                    "expected alphabetic or numeric character, but found " + s + "("
                            + c + ")", reader.getMark());
        }
        String value = reader.prefixForward(length);
        c = reader.peek();
        if (CharConstants.NULL_BL_T_LINEBR.hasNo(c, "?:,]}%@`")) {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning an " + name, startMark,
                    "expected alphabetic or numeric character, but found " + s + "("
                            + c + ")", reader.getMark());
        }
        Optional<Mark> endMark = reader.getMark();
        Token tok;
        if (isAnchor) {
            tok = new AnchorToken(new Anchor(value), startMark, endMark);
        } else {
            tok = new AliasToken(new Anchor(value), startMark, endMark);
        }
        return tok;
    }

    /**
     * <p>
     * Scan a Tag property. A Tag property may be specified in one of three
     * ways: c-verbatim-tag, c-ns-shorthand-tag, or c-ns-non-specific-tag
     * </p>
     * <p>
     * <p>
     * c-verbatim-tag takes the form !&lt;ns-uri-char+&gt; and must be delivered
     * verbatim (as-is) to the application. In particular, verbatim tags are not
     * subject to tag resolution.
     * </p>
     * <p>
     * <p>
     * c-ns-shorthand-tag is a valid tag handle followed by a non-empty suffix.
     * If the tag handle is a c-primary-tag-handle ('!') then the suffix must
     * have all exclamation marks properly URI-escaped (%21); otherwise, the
     * string will look like a named tag handle: !foo!bar would be interpreted
     * as (handle="!foo!", suffix="bar").
     * </p>
     * <p>
     * <p>
     * c-ns-non-specific-tag is always a lone '!'; this is only useful for plain
     * scalars, where its specification means that the scalar MUST be resolved
     * to have type tag:yaml.org,2002:str.
     * </p>
     * <p>
     * TODO SnakeYAML incorrectly ignores c-ns-non-specific-tag right now.
     * <p>
     * <p>
     * TODO Note that this method does not enforce rules about local versus global tags!
     */
    private Token scanTag() {
        // See the specification for details.
        Optional<Mark> startMark = reader.getMark();
        // Determine the type of tag property based on the first character
        // encountered
        int c = reader.peek(1);
        String handle = null;
        String suffix = null;
        // Verbatim tag! (c-verbatim-tag)
        if (c == '<') {
            // Skip the exclamation mark and &gt;, then read the tag suffix (as
            // a URI).
            reader.forward(2);
            suffix = scanTagUri("tag", startMark);
            c = reader.peek();
            if (c != '>') {
                // If there are any characters between the end of the tag-suffix
                // URI and the closing &gt;, then an error has occurred.
                final String s = String.valueOf(Character.toChars(c));
                throw new ScannerException("while scanning a tag", startMark,
                        "expected '>', but found '" + s + "' (" + c
                                + ")", reader.getMark());
            }
            reader.forward();
        } else if (CharConstants.NULL_BL_T_LINEBR.has(c)) {
            // A NUL, blank, tab, or line-break means that this was a
            // c-ns-non-specific tag.
            suffix = "!";
            reader.forward();
        } else {
            // Any other character implies c-ns-shorthand-tag type.

            // Look ahead in the stream to determine whether this tag property
            // is of the form !foo or !foo!bar.
            int length = 1;
            boolean useHandle = false;
            while (CharConstants.NULL_BL_LINEBR.hasNo(c)) {
                if (c == '!') {
                    useHandle = true;
                    break;
                }
                length++;
                c = reader.peek(length);
            }
            // If we need to use a handle, scan it in; otherwise, the handle is
            // presumed to be '!'.
            if (useHandle) {
                handle = scanTagHandle("tag", startMark);
            } else {
                handle = "!";
                reader.forward();
            }
            suffix = scanTagUri("tag", startMark);
        }
        c = reader.peek();
        // Check that the next character is allowed to follow a tag-property;
        // if it is not, raise the error.
        if (CharConstants.NULL_BL_LINEBR.hasNo(c)) {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a tag", startMark,
                    "expected ' ', but found '" + s + "' (" + (c) + ")", reader.getMark());
        }
        TagTuple value = new TagTuple(handle, suffix);
        Optional<Mark> endMark = reader.getMark();
        return new TagToken(value, startMark, endMark);
    }

    private Token scanBlockScalar(ScalarStyle style) {
        // See the specification for details.
        boolean folded;
        StringBuilder chunks = new StringBuilder();
        Optional<Mark> startMark = reader.getMark();
        // Scan the header.
        reader.forward();
        Chomping chompi = scanBlockScalarIndicators(startMark);
        int increment = chompi.getIncrement();
        scanBlockScalarIgnoredLine(startMark);

        // Determine the indentation level and go to the first non-empty line.
        int minIndent = this.indent + 1;
        if (minIndent < 1) {
            minIndent = 1;
        }
        String breaks;
        int maxIndent;
        int indent;
        Optional<Mark> endMark;
        if (increment == -1) {
            Object[] brme = scanBlockScalarIndentation();
            breaks = (String) brme[0];
            maxIndent = ((Integer) brme[1]).intValue();
            endMark = (Optional<Mark>) brme[2];
            indent = Math.max(minIndent, maxIndent);
        } else {
            indent = minIndent + increment - 1;
            Object[] brme = scanBlockScalarBreaks(indent);
            breaks = (String) brme[0];
            endMark = (Optional<Mark>) brme[1];
        }

        String lineBreak = "";

        // Scan the inner part of the block scalar.
        while (this.reader.getColumn() == indent && reader.peek() != '\0') {
            chunks.append(breaks);
            boolean leadingNonSpace = " \t".indexOf(reader.peek()) == -1;
            int length = 0;
            while (CharConstants.NULL_OR_LINEBR.hasNo(reader.peek(length))) {
                length++;
            }
            chunks.append(reader.prefixForward(length));
            lineBreak = scanLineBreak();
            Object[] brme = scanBlockScalarBreaks(indent);
            breaks = (String) brme[0];
            endMark = (Optional<Mark>) brme[1];
            if (this.reader.getColumn() == indent && reader.peek() != '\0') {

                // Unfortunately, folding rules are ambiguous.
                //
                // This is the folding according to the specification:
                if (style == ScalarStyle.FOLDED && "\n".equals(lineBreak) && leadingNonSpace
                        && " \t".indexOf(reader.peek()) == -1) {
                    if (breaks.length() == 0) {
                        chunks.append(" ");
                    }
                } else {
                    chunks.append(lineBreak);
                }
            } else {
                break;
            }
        }
        // Chomp the tail.
        if (chompi.chompTailIsNotFalse()) {
            chunks.append(lineBreak);
        }
        if (chompi.chompTailIsTrue()) {
            chunks.append(breaks);
        }
        // We are done.
        return new ScalarToken(chunks.toString(), false, style, startMark, endMark);
    }

    /**
     * Scan a block scalar indicator. The block scalar indicator includes two
     * optional components, which may appear in either order.
     * <p>
     * A block indentation indicator is a non-zero digit describing the
     * indentation level of the block scalar to follow. This indentation is an
     * additional number of spaces relative to the current indentation level.
     * <p>
     * A block chomping indicator is a + or -, selecting the chomping mode away
     * from the default (clip) to either -(strip) or +(keep).
     */
    private Chomping scanBlockScalarIndicators(Optional<Mark> startMark) {
        // See the specification for details.
        Boolean chomping = null;
        int increment = -1;
        int c = reader.peek();
        if (c == '-' || c == '+') {
            if (c == '+') {
                chomping = Boolean.TRUE;
            } else {
                chomping = Boolean.FALSE;
            }
            reader.forward();
            c = reader.peek();
            if (Character.isDigit(c)) {
                final String s = String.valueOf(Character.toChars(c));
                increment = Integer.parseInt(s);
                if (increment == 0) {
                    throw new ScannerException("while scanning a block scalar", startMark,
                            "expected indentation indicator in the range 1-9, but found 0",
                            reader.getMark());
                }
                reader.forward();
            }
        } else if (Character.isDigit(c)) {
            final String s = String.valueOf(Character.toChars(c));
            increment = Integer.parseInt(s);
            if (increment == 0) {
                throw new ScannerException("while scanning a block scalar", startMark,
                        "expected indentation indicator in the range 1-9, but found 0",
                        reader.getMark());
            }
            reader.forward();
            c = reader.peek();
            if (c == '-' || c == '+') {
                if (c == '+') {
                    chomping = Boolean.TRUE;
                } else {
                    chomping = Boolean.FALSE;
                }
                reader.forward();
            }
        }
        c = reader.peek();
        if (CharConstants.NULL_BL_LINEBR.hasNo(c)) {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a block scalar", startMark,
                    "expected chomping or indentation indicators, but found " + s + "("
                            + c + ")", reader.getMark());
        }
        return new Chomping(chomping, increment);
    }

    /**
     * Scan to the end of the line after a block scalar has been scanned; the
     * only things that are permitted at this time are comments and spaces.
     */
    private String scanBlockScalarIgnoredLine(Optional<Mark> startMark) {
        // See the specification for details.

        // Forward past any number of trailing spaces
        while (reader.peek() == ' ') {
            reader.forward();
        }

        // If a comment occurs, scan to just before the end of line.
        if (reader.peek() == '#') {
            while (CharConstants.NULL_OR_LINEBR.hasNo(reader.peek())) {
                reader.forward();
            }
        }
        // If the next character is not a null or line break, an error has
        // occurred.
        int c = reader.peek();
        String lineBreak = scanLineBreak();
        if (lineBreak.length() == 0 && c != '\0') {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a block scalar", startMark,
                    "expected a comment or a line break, but found " + s + "("
                            + c + ")", reader.getMark());
        }
        return lineBreak;
    }

    /**
     * Scans for the indentation of a block scalar implicitly. This mechanism is
     * used only if the block did not explicitly state an indentation to be
     * used.
     */
    private Object[] scanBlockScalarIndentation() {
        // See the specification for details.
        StringBuilder chunks = new StringBuilder();
        int maxIndent = 0;
        Optional<Mark> endMark = reader.getMark();
        // Look ahead some number of lines until the first non-blank character
        // occurs; the determined indentation will be the maximum number of
        // leading spaces on any of these lines.
        while (CharConstants.LINEBR.has(reader.peek(), " \r")) {
            if (reader.peek() != ' ') {
                // If the character isn't a space, it must be some kind of
                // line-break; scan the line break and track it.
                chunks.append(scanLineBreak());
                endMark = reader.getMark();
            } else {
                // If the character is a space, move forward to the next
                // character; if we surpass our previous maximum for indent
                // level, update that too.
                reader.forward();
                if (this.reader.getColumn() > maxIndent) {
                    maxIndent = reader.getColumn();
                }
            }
        }
        // Pass several results back together.
        return new Object[]{chunks.toString(), maxIndent, endMark};
    }

    private Object[] scanBlockScalarBreaks(int indent) {
        // See the specification for details.
        StringBuilder chunks = new StringBuilder();
        Optional<Mark> endMark = reader.getMark();
        int col = this.reader.getColumn();
        // Scan for up to the expected indentation-level of spaces, then move
        // forward past that amount.
        while (col < indent && reader.peek() == ' ') {
            reader.forward();
            col++;
        }

        // Consume one or more line breaks followed by any amount of spaces,
        // until we find something that isn't a line-break.
        String lineBreak = null;
        while ((lineBreak = scanLineBreak()).length() != 0) {
            chunks.append(lineBreak);
            endMark = reader.getMark();
            // Scan past up to (indent) spaces on the next line, then forward
            // past them.
            col = this.reader.getColumn();
            while (col < indent && reader.peek() == ' ') {
                reader.forward();
                col++;
            }
        }
        // Return both the assembled intervening string and the end-mark.
        return new Object[]{chunks.toString(), endMark};
    }

    /**
     * Scan a flow-style scalar. Flow scalars are presented in one of two forms;
     * first, a flow scalar may be a double-quoted string; second, a flow scalar
     * may be a single-quoted string.
     *
     *
     * <pre>
     * See the specification for details.
     * Note that we loose indentation rules for quoted scalars. Quoted
     * scalars don't need to adhere indentation because &quot; and ' clearly
     * mark the beginning and the end of them. Therefore we are less
     * restrictive then the specification requires. We only need to check
     * that document separators are not included in scalars.
     * </pre>
     */
    private Token scanFlowScalar(ScalarStyle style) {
        boolean _double;
        // The style will be either single- or double-quoted; we determine this
        // by the first character in the entry (supplied)
        if (style == ScalarStyle.DOUBLE_QUOTED) {
            _double = true;
        } else {
            _double = false;
        }
        StringBuilder chunks = new StringBuilder();
        Optional<Mark> startMark = reader.getMark();
        int quote = reader.peek();
        reader.forward();
        chunks.append(scanFlowScalarNonSpaces(_double, startMark));
        while (reader.peek() != quote) {
            chunks.append(scanFlowScalarSpaces(startMark));
            chunks.append(scanFlowScalarNonSpaces(_double, startMark));
        }
        reader.forward();
        Optional<Mark> endMark = reader.getMark();
        return new ScalarToken(chunks.toString(), false, style, startMark, endMark);
    }

    /**
     * Scan some number of flow-scalar non-space characters.
     */
    private String scanFlowScalarNonSpaces(boolean doubleQuoted, Optional<Mark> startMark) {
        // See the specification for details.
        StringBuilder chunks = new StringBuilder();
        while (true) {
            // Scan through any number of characters which are not: NUL, blank,
            // tabs, line breaks, single-quotes, double-quotes, or backslashes.
            int length = 0;
            while (CharConstants.NULL_BL_T_LINEBR.hasNo(reader.peek(length), "\'\"\\")) {
                length++;
            }
            if (length != 0) {
                chunks.append(reader.prefixForward(length));
            }
            // Depending on our quoting-type, the characters ', " and \ have
            // differing meanings.
            int c = reader.peek();
            if (!doubleQuoted && c == '\'' && reader.peek(1) == '\'') {
                chunks.append("'");
                reader.forward(2);
            } else if ((doubleQuoted && c == '\'') || (!doubleQuoted && "\"\\".indexOf(c) != -1)) {
                chunks.appendCodePoint(c);
                reader.forward();
            } else if (doubleQuoted && c == '\\') {
                reader.forward();
                c = reader.peek();
                if (!Character.isSupplementaryCodePoint(c) && ESCAPE_REPLACEMENTS.containsKey(c)) {
                    // The character is one of the single-replacement
                    // types; these are replaced with a literal character
                    // from the mapping.
                    chunks.append(ESCAPE_REPLACEMENTS.get(c));
                    reader.forward();
                } else if (!Character.isSupplementaryCodePoint(c) && ESCAPE_CODES.containsKey(Character.valueOf((char) c))) {
                    // The character is a multi-digit escape sequence, with
                    // length defined by the value in the ESCAPE_CODES map.
                    length = ESCAPE_CODES.get(Character.valueOf((char) c)).intValue();
                    reader.forward();
                    String hex = reader.prefix(length);
                    if (NOT_HEXA.matcher(hex).find()) {
                        throw new ScannerException("while scanning a double-quoted scalar",
                                startMark, "expected escape sequence of " + length
                                + " hexadecimal numbers, but found: " + hex,
                                reader.getMark());
                    }
                    int decimal = Integer.parseInt(hex, 16);
                    String unicode = new String(Character.toChars(decimal));
                    chunks.append(unicode);
                    reader.forward(length);
                } else if (scanLineBreak().length() != 0) {
                    chunks.append(scanFlowScalarBreaks(startMark));
                } else {
                    final String s = String.valueOf(Character.toChars(c));
                    throw new ScannerException("while scanning a double-quoted scalar", startMark,
                            "found unknown escape character " + s + "(" + c + ")",
                            reader.getMark());
                }
            } else {
                return chunks.toString();
            }
        }
    }

    private String scanFlowScalarSpaces(Optional<Mark> startMark) {
        // See the specification for details.
        StringBuilder chunks = new StringBuilder();
        int length = 0;
        // Scan through any number of whitespace (space, tab) characters,
        // consuming them.
        while (" \t".indexOf(reader.peek(length)) != -1) {
            length++;
        }
        String whitespaces = reader.prefixForward(length);
        int c = reader.peek();
        if (c == '\0') {
            // A flow scalar cannot end with an end-of-stream
            throw new ScannerException("while scanning a quoted scalar", startMark,
                    "found unexpected end of stream", reader.getMark());
        }
        // If we encounter a line break, scan it into our assembled string...
        String lineBreak = scanLineBreak();
        if (lineBreak.length() != 0) {
            String breaks = scanFlowScalarBreaks(startMark);
            if (!"\n".equals(lineBreak)) {
                chunks.append(lineBreak);
            } else if (breaks.length() == 0) {
                chunks.append(" ");
            }
            chunks.append(breaks);
        } else {
            chunks.append(whitespaces);
        }
        return chunks.toString();
    }

    private String scanFlowScalarBreaks(Optional<Mark> startMark) {
        // See the specification for details.
        StringBuilder chunks = new StringBuilder();
        while (true) {
            // Instead of checking indentation, we check for document
            // separators.
            String prefix = reader.prefix(3);
            if (("---".equals(prefix) || "...".equals(prefix))
                    && CharConstants.NULL_BL_T_LINEBR.has(reader.peek(3))) {
                throw new ScannerException("while scanning a quoted scalar", startMark,
                        "found unexpected document separator", reader.getMark());
            }
            // Scan past any number of spaces and tabs, ignoring them
            while (" \t".indexOf(reader.peek()) != -1) {
                reader.forward();
            }
            // If we stopped at a line break, add that; otherwise, return the
            // assembled set of scalar breaks.
            String lineBreak = scanLineBreak();
            if (lineBreak.length() != 0) {
                chunks.append(lineBreak);
            } else {
                return chunks.toString();
            }
        }
    }

    /**
     * Scan a plain scalar.
     *
     * <pre>
     * See the specification for details.
     * We add an additional restriction for the flow context:
     *   plain scalars in the flow context cannot contain ',', ':' and '?'.
     * We also keep track of the `allow_simple_key` flag here.
     * Indentation rules are loosed for the flow context.
     * </pre>
     */
    private Token scanPlain() {
        StringBuilder chunks = new StringBuilder();
        Optional<Mark> startMark = reader.getMark();
        Optional<Mark> endMark = startMark;
        int indent = this.indent + 1;
        String spaces = "";
        while (true) {
            int c;
            int length = 0;
            // A comment indicates the end of the scalar.
            if (reader.peek() == '#') {
                break;
            }
            while (true) {
                c = reader.peek(length);
                if (CharConstants.NULL_BL_T_LINEBR.has(c)
                        || (c == ':' && CharConstants.NULL_BL_T_LINEBR.has(reader.peek(length + 1), flowLevel != 0 ? ",[]{}" : ""))
                        || (this.flowLevel != 0 && ",?[]{}".indexOf(c) != -1)) {
                    break;
                }
                length++;
            }
            if (length == 0) {
                break;
            }
            this.allowSimpleKey = false;
            chunks.append(spaces);
            chunks.append(reader.prefixForward(length));
            endMark = reader.getMark();
            spaces = scanPlainSpaces();
            // System.out.printf("spaces[%s]\n", spaces);
            if (spaces.length() == 0 || reader.peek() == '#'
                    || (this.flowLevel == 0 && this.reader.getColumn() < indent)) {
                break;
            }
        }
        return new ScalarToken(chunks.toString(),true, startMark, endMark);
    }

    /**
     * See the specification for details. SnakeYAML and libyaml allow tabs
     * inside plain scalar
     */
    private String scanPlainSpaces() {
        int length = 0;
        while (reader.peek(length) == ' ' || reader.peek(length) == '\t') {
            length++;
        }
        String whitespaces = reader.prefixForward(length);
        String lineBreak = scanLineBreak();
        if (lineBreak.length() != 0) {
            this.allowSimpleKey = true;
            String prefix = reader.prefix(3);
            if ("---".equals(prefix) || "...".equals(prefix)
                    && CharConstants.NULL_BL_T_LINEBR.has(reader.peek(3))) {
                return "";
            }
            StringBuilder breaks = new StringBuilder();
            while (true) {
                if (reader.peek() == ' ') {
                    reader.forward();
                } else {
                    String lb = scanLineBreak();
                    if (lb.length() != 0) {
                        breaks.append(lb);
                        prefix = reader.prefix(3);
                        if ("---".equals(prefix) || "...".equals(prefix)
                                && CharConstants.NULL_BL_T_LINEBR.has(reader.peek(3))) {
                            return "";
                        }
                    } else {
                        break;
                    }
                }
            }
            if (!"\n".equals(lineBreak)) {
                return lineBreak + breaks;
            } else if (breaks.length() == 0) {
                return " ";
            }
            return breaks.toString();
        }
        return whitespaces;
    }

    /**
     * <p>
     * Scan a Tag handle. A Tag handle takes one of three forms:
     * <p>
     * <pre>
     * "!" (c-primary-tag-handle)
     * "!!" (ns-secondary-tag-handle)
     * "!(name)!" (c-named-tag-handle)
     * </pre>
     * <p>
     * Where (name) must be formatted as an ns-word-char.
     * </p>
     *
     *
     * <pre>
     * See the specification for details.
     * For some strange reasons, the specification does not allow '_' in
     * tag handles. I have allowed it anyway.
     * </pre>
     */
    private String scanTagHandle(String name, Optional<Mark> startMark) {
        int c = reader.peek();
        if (c != '!') {
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a " + name, startMark,
                    "expected '!', but found " + s + "(" + (c) + ")", reader.getMark());
        }
        // Look for the next '!' in the stream, stopping if we hit a
        // non-word-character. If the first character is a space, then the
        // tag-handle is a c-primary-tag-handle ('!').
        int length = 1;
        c = reader.peek(length);
        if (c != ' ') {
            // Scan through 0+ alphabetic characters.
            // FIXME According to the specification, these should be
            // ns-word-char only, which prohibits '_'. This might be a
            // candidate for a configuration option.
            while (CharConstants.ALPHA.has(c)) {
                length++;
                c = reader.peek(length);
            }
            // Found the next non-word-char. If this is not a space and not an
            // '!', then this is an error, as the tag-handle was specified as:
            // !(name) or similar; the trailing '!' is missing.
            if (c != '!') {
                reader.forward(length);
                final String s = String.valueOf(Character.toChars(c));
                throw new ScannerException("while scanning a " + name, startMark,
                        "expected '!', but found " + s + "(" + (c) + ")", reader.getMark());
            }
            length++;
        }
        String value = reader.prefixForward(length);
        return value;
    }

    /**
     * <p>
     * Scan a Tag URI. This scanning is valid for both local and global tag
     * directives, because both appear to be valid URIs as far as scanning is
     * concerned. The difference may be distinguished later, in parsing. This
     * method will scan for ns-uri-char*, which covers both cases.
     * </p>
     * <p>
     * <p>
     * This method performs no verification that the scanned URI conforms to any
     * particular kind of URI specification.
     * </p>
     */
    private String scanTagUri(String name, Optional<Mark> startMark) {
        // See the specification for details.
        // Note: we do not check if URI is well-formed.
        StringBuilder chunks = new StringBuilder();
        // Scan through accepted URI characters, which includes the standard
        // URI characters, plus the start-escape character ('%'). When we get
        // to a start-escape, scan the escaped sequence, then return.
        int length = 0;
        int c = reader.peek(length);
        while (CharConstants.URI_CHARS.has(c)) {
            if (c == '%') {
                chunks.append(reader.prefixForward(length));
                length = 0;
                chunks.append(scanUriEscapes(name, startMark));
            } else {
                length++;
            }
            c = reader.peek(length);
        }
        // Consume the last "chunk", which would not otherwise be consumed by
        // the loop above.
        if (length != 0) {
            chunks.append(reader.prefixForward(length));
        }
        if (chunks.length() == 0) {
            // If no URI was found, an error has occurred.
            final String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a " + name, startMark,
                    "expected URI, but found " + s + "(" + (c) + ")", reader.getMark());
        }
        return chunks.toString();
    }

    /**
     * <p>
     * Scan a sequence of %-escaped URI escape codes and convert them into a
     * String representing the unescaped values.
     * </p>
     * <p>
     * FIXME This method fails for more than 256 bytes' worth of URI-encoded
     * characters in a row. Is this possible? Is this a use-case?
     */
    private String scanUriEscapes(String name, Optional<Mark> startMark) {
        // First, look ahead to see how many URI-escaped characters we should
        // expect, so we can use the correct buffer size.
        int length = 1;
        while (reader.peek(length * 3) == '%') {
            length++;
        }
        // See the specification for details.
        // URIs containing 16 and 32 bit Unicode characters are
        // encoded in UTF-8, and then each octet is written as a
        // separate character.
        Optional<Mark> beginningMark = reader.getMark();
        ByteBuffer buff = ByteBuffer.allocate(length);
        while (reader.peek() == '%') {
            reader.forward();
            try {
                byte code = (byte) Integer.parseInt(reader.prefix(2), 16);
                buff.put(code);
            } catch (NumberFormatException nfe) {
                int c1 = reader.peek();
                final String s1 = String.valueOf(Character.toChars(c1));
                int c2 = reader.peek(1);
                final String s2 = String.valueOf(Character.toChars(c2));
                throw new ScannerException("while scanning a " + name, startMark,
                        "expected URI escape sequence of 2 hexadecimal numbers, but found "
                                + s1 + "(" + c1 + ") and "
                                + s2 + "(" + c2 + ")",
                        reader.getMark());
            }
            reader.forward(2);
        }
        buff.flip();
        try {
            return UriEncoder.decode(buff);
        } catch (CharacterCodingException e) {
            throw new ScannerException("while scanning a " + name, startMark,
                    "expected URI in UTF-8: " + e.getMessage(), beginningMark);
        }
    }

    /**
     * Scan a line break, transforming:
     *
     * <pre>
     * '\r\n' : '\n'
     * '\r' : '\n'
     * '\n' : '\n'
     * '\x85' : '\n'
     * default : ''
     * </pre>
     */
    private String scanLineBreak() {
        // Transforms:
        // '\r\n' : '\n'
        // '\r' : '\n'
        // '\n' : '\n'
        // '\x85' : '\n'
        // default : ''
        int c = reader.peek();
        if (c == '\r' || c == '\n' || c == '\u0085') {
            if (c == '\r' && '\n' == reader.peek(1)) {
                reader.forward(2);
            } else {
                reader.forward();
            }
            return "\n";
        } else if (c == '\u2028' || c == '\u2029') {
            reader.forward();
            return String.valueOf(Character.toChars(c));
        }
        return "";
    }

    /**
     * Chomping the tail may have 3 values - yes, no, not defined.
     */
    private static class Chomping {
        private final Boolean value;
        private final int increment;

        public Chomping(Boolean value, int increment) {
            this.value = value;
            this.increment = increment;
        }

        public boolean chompTailIsNotFalse() {
            return value == null || value;
        }

        public boolean chompTailIsTrue() {
            return value != null && value;
        }

        public int getIncrement() {
            return increment;
        }
    }
}
