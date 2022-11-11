import abc
import logging
import os
from typing import Tuple, List, Type, Optional, Iterator

from data_processing.datapoint import CodeDataPoint
from data_processing.exceptions import BadContextError
from data_processing.processors.abstract_processor import ProcessorBase, TrainableProcessorBase
from data_processing.processors.black_styler import BlackStylerBase
from data_processing.processors.comment_remover import CommentRemoverBase
from data_processing.processors.empty_line_remover import EmptyLineRemoverBase
from data_processing.processors.indent_remover import IndentRemoverBase
from data_processing.processors.yttm_tokenizer import YttmTokenizerBase
from tokenizer.text_bpe import TextBPE

logger = logging.getLogger(__name__)


class SuffixRollbackBase(TrainableProcessorBase):
    @abc.abstractmethod
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        raise NotImplementedError

    @staticmethod
    def is_deterministic() -> bool:
        return True

    @staticmethod
    def is_slow() -> bool:
        return False

    @staticmethod
    def must_be_after() -> List[Type[ProcessorBase]]:
        # Basically, after all processors that modifying content
        return [BlackStylerBase, CommentRemoverBase, EmptyLineRemoverBase, IndentRemoverBase]

    @staticmethod
    def must_be_before() -> List[Type[ProcessorBase]]:
        return [YttmTokenizerBase]

    @staticmethod
    def _train(dps: Iterator[CodeDataPoint], save_path: str, **train_kwargs) -> None:
        pass

    def _load(self, path: str) -> None:
        self._tokenizer = TextBPE(path)

    @staticmethod
    def get_path(dir_path: str) -> str:
        return os.path.join(dir_path, "yttmbpe.bpe")

    @staticmethod
    def requires_data_split() -> bool:
        pass


class SuffixRollbackTrainData(SuffixRollbackBase):
    def __init__(self, dir_path: str, rollback_strategy: str):
        # Explicitly don't call base constructor since YttmTokenizer isn't saved yet, and we can't load it
        pass

    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return dp


class SuffixRollbackTrainModel(SuffixRollbackBase):
    def __init__(self, dir_path: str, rollback_strategy: str):
        super().__init__(dir_path)

    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return dp


class SuffixRollbackInference(SuffixRollbackBase):
    def __init__(self, dir_path: str, rollback_strategy: str):
        super().__init__(dir_path)
        if rollback_strategy == "longest":
            self._rollbacker = LongestRollback(self._tokenizer)
        else:
            raise ValueError(f"Unknown rollback strategy: {rollback_strategy}")

    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        dp.content, dp.suffix = self._rollbacker.rollback(dp.content)
        dp.suffix = dp.suffix if dp.suffix else None
        return dp


class ContextRollback(abc.ABC):
    def __init__(self, tokenizer: TextBPE):
        self._tokenizer = tokenizer

    def rollback(self, context: str) -> Tuple[str, str]:
        try:
            start_id = max(context.rfind(sep_char) + 1 for sep_char in self._tokenizer.sep_chars)
        except ValueError:
            raise BadContextError(f"Can't find char {repr(self._tokenizer.sep_chars)} in context")
        last_token = context[start_id:]
        prefix, suffix = self._rollback(last_token)
        logger.debug(f" Rolling back token {repr(last_token)}")
        logger.debug(f" Found suffix: {repr(suffix)}")
        return context[:start_id] + prefix, suffix

    @abc.abstractmethod
    def _rollback(self, context: str) -> Tuple[str, str]:
        raise NotImplementedError


class LongestRollback(ContextRollback):
    def _rollback(self, context: str) -> Tuple[str, str]:
        if not context:
            return context, ""

        for i in range(len(context)):
            suffix = context[i:]
            _, exact_prefix, over_prefix = self._tokenizer.prefix_to_ids(suffix)
            if exact_prefix is not None:
                over_prefix.append(exact_prefix)

            found_ids = self._filter_suffixes(context[:i], over_prefix)
            if found_ids:
                if len(found_ids) == 1 and self._tokenizer.id_to_token(found_ids[0]) == suffix:
                    # The last token is a full token, suffix isn't needed
                    return context, ""
                return context[:i], suffix
        raise BadContextError(f"Can't find longest suffix")

    def _filter_suffixes(self, prefix: str, suffix_ids: List[int]) -> List[int]:
        if not suffix_ids:
            return suffix_ids
        strings = [prefix + self._tokenizer.id_to_token(suffix_id) for suffix_id in suffix_ids]
        encoded_strings = self._tokenizer.encode(strings, bos=False, eos=False, dropout_prob=0.0)
        return [suffix_id for suffix_id, ids in zip(suffix_ids, encoded_strings) if ids[-1] == suffix_id]
