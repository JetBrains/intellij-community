
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = CanonicalNames.MyDeserializer.class)
public class CanonicalNames {

    /**
     * Type: {@code NameConverter | NameConversionRulesSingle}
     * 
     */
    private Object value;

    /**
     * Type: {@code NameConverter | NameConversionRulesSingle}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code NameConverter | NameConversionRulesSingle}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends JsonDeserializer<CanonicalNames>
    {


        @Override
        public CanonicalNames deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            CanonicalNames result = new CanonicalNames();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_STRING) {
                result.value = parser.readValueAs(NameConversionRulesSingle.NameConverter.class);
            } else {
                if (token == JsonToken.START_OBJECT) {
                    result.value = parser.readValueAs(NameConversionRulesSingle.class);
                } else {
                    deserializationContext.handleUnexpectedToken(Object.class, parser);
                }
            }
            return result;
        }

    }

}
