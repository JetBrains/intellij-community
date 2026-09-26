
package com.intellij.polySymbols.webTypes.json;

import java.util.ArrayList;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = ListReference.MyDeserializer.class)
public class ListReference
    extends ArrayList<Reference>
{


    public static class MyDeserializer
        extends ValueDeserializer<ListReference>
    {


        @Override
        public ListReference deserialize(JsonParser parser, DeserializationContext deserializationContext)
        {
            ListReference result = new ListReference();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_ARRAY) {
                while (parser.nextToken()!= JsonToken.END_ARRAY) {
                    token = parser.currentToken();
                    if ((token == JsonToken.VALUE_STRING)||(token == JsonToken.START_OBJECT)) {
                        result.add(parser.readValueAs(Reference.class));
                    } else {
                        deserializationContext.handleUnexpectedToken(Reference.class, parser);
                    }
                }
            } else {
                if ((token == JsonToken.VALUE_STRING)||(token == JsonToken.START_OBJECT)) {
                    result.add(parser.readValueAs(Reference.class));
                } else {
                    deserializationContext.handleUnexpectedToken(Reference.class, parser);
                }
            }
            return result;
        }

    }

}
