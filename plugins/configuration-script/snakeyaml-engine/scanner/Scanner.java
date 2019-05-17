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


import java.util.Iterator;

import org.snakeyaml.engine.v1.exceptions.ScannerException;
import org.snakeyaml.engine.v1.tokens.Token;

/**
 * This interface represents an input stream of {@link Token}s.
 * <p>
 * The scanner and the scanner form together the 'Parse' step in the loading process.
 * </p>
 *
 * @see org.snakeyaml.engine.v1.tokens.Token
 */
public interface Scanner extends Iterator<Token> {

    /**
     * Check if the next token is one of the given types.
     *
     * @param choices token IDs.
     * @return <code>true</code> if the next token can be assigned to a variable
     * of at least one of the given types. Returns <code>false</code> if
     * no more tokens are available.
     * @throws ScannerException Thrown in case of malformed input.
     */
    boolean checkToken(Token.ID... choices);

    /**
     * Return the next token, but do not delete it from the stream.
     * The method must be called only after {@link #checkToken}.
     *
     * @return The token that will be returned on the next call to {@link #next}
     * @throws ScannerException          Thrown in case of malformed input.
     * @throws IndexOutOfBoundsException if no more token left
     */
    Token peekToken();

    /**
     * Returns the next token.
     * <p>
     * The token will be removed from the stream.
     * (Every invocation of this method must happen after calling {@link #checkToken}.
     * </p>
     *
     * @return the coming token
     * @throws ScannerException          Thrown in case of malformed input.
     * @throws IndexOutOfBoundsException if no more token left
     */
    Token next();
}
