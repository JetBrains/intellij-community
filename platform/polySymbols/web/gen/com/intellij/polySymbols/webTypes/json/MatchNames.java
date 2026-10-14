
package com.intellij.polySymbols.webTypes.json;

import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

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
        extends ValueDeserializer<MatchNames>
    {


        @Override
        public MatchNames deserialize(JsonParser parser, DeserializationContext deserializationContext)
        {
            MatchNames result = new MatchNames();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_ARRAY) {
                result.value = deserializationContext.readValue(parser, deserializationContext.getTypeFactory().constructParametricType(List.class, com.intellij.polySymbols.webTypes.json.NameConversionRulesSingle.NameConverter.class));
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
