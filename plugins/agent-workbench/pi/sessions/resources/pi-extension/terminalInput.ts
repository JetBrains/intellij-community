import {type ExtensionContext} from "@earendil-works/pi-coding-agent";

const WORKBENCH_SHIFT_ENTER_SEQUENCE = "\x1b\r";
const KITTY_SHIFT_ENTER_SEQUENCE = "\x1b[13;2u";

export function subscribeShiftEnterTerminalInput(ctx: ExtensionContext): () => void {
  return ctx.ui.onTerminalInput((data) => {
    if (data === WORKBENCH_SHIFT_ENTER_SEQUENCE) {
      return {data: KITTY_SHIFT_ENTER_SEQUENCE};
    }
    return undefined;
  });
}
