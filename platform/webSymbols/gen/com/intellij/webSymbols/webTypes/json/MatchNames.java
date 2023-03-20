
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import java.util.List;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = MatchNames.MyDeserializer.class)
public class MatchNames {

    /**
     * Type: {@code List<NameConverter> | NameConversionRulesMultiple}
     * 
     */
    private Object value;

    /**
     * Type: {@code List<NameConverter> | NameConversionRulesMultiple}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code List<NameConverter> | NameConversionRulesMultiple}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends JsonDeserializer<MatchNames>
    {


        @Override
        public MatchNames deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            MatchNames result = new MatchNames();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_ARRAY) {
                result.value = parser.getCodec().readValue(parser, deserializationContext.getTypeFactory().constructParametricType(List.class, com.intellij.webSymbols.webTypes.json.NameConversionRulesSingle.NameConverter.class));
            } else {
                if (token == JsonToken.START_OBJECT) {
                    result.value = parser.readValueAs(NameConversionRulesMultiple.class);
                } else {
                    deserializationContext.handleUnexpectedToken(Object.class, parser);
                }
            }
            return result;
        }

    }

}
